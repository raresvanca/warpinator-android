package slowscript.warpinator.core.network.worker

import android.app.PendingIntent
import android.content.Intent
import android.media.MediaScannerConnection
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.google.common.io.Files
import com.google.protobuf.ByteString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import slowscript.warpinator.BuildConfig
import slowscript.warpinator.R
import slowscript.warpinator.WarpProto
import slowscript.warpinator.app.MainActivity
import slowscript.warpinator.core.data.WarpinatorRepository
import slowscript.warpinator.core.model.Transfer
import slowscript.warpinator.core.model.Transfer.Direction
import slowscript.warpinator.core.model.Transfer.Error
import slowscript.warpinator.core.model.Transfer.FileType
import slowscript.warpinator.core.model.Transfer.Status
import slowscript.warpinator.core.service.MainService
import slowscript.warpinator.core.service.RemotesManager
import slowscript.warpinator.core.utils.Utils
import slowscript.warpinator.core.utils.ZlibCompressor
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.URLConnection
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.Deflater.DEFAULT_COMPRESSION
import kotlin.math.max

class TransferWorker(
    initialTransfer: Transfer,
    private val repository: WarpinatorRepository,
    private val remotesManager: RemotesManager
) {

    // We keep a local mutable copy for logic, but expose updates via Repository
    private var transferData: Transfer = initialTransfer
    private val status = AtomicReference(initialTransfer.status)

    // Internal lists for logic
    private val files = ArrayList<MFile>()
    private val dirs = ArrayList<MFile>()
    private val topDirBaseNames = ArrayList<String>()
    private val receivedPaths = ArrayList<String>()
    private val errorList = ArrayList<String>()

    // State variables
    private var currentRelativePath: String? = null
    private var currentLastMod: Long = -1
    private var currentUri: Uri? = null
    private var currentFile: File? = null
    private var currentStream: OutputStream? = null
    private var cancelled = false
    private var safeOverwriteFlag = false

    // Progress
    private val transferSpeed = TransferSpeed(24)
    private var actualStartTime: Long = 0
    private var lastBytes: Long = 0
    private var lastMillis: Long = 0
    private var lastUiUpdate: Long = 0

    fun stop(error: Boolean) {
        Log.i(TAG, "Transfer stopped")
        remotesManager.getWorker(transferData.remoteUuid)?.stopTransfer(transferData, error)
        onStopped(error)
    }

    fun onStopped(error: Boolean) {
        Log.v(TAG, "Stopping transfer")
        if (!error) setStatus(Status.Stopped)
        if (transferData.direction == Direction.Receive) stopReceiving()
        else stopSending()
        updateRepo()
    }

    fun makeDeclined() {
        setStatus(Status.Declined)
        updateRepo()
    }

    private fun updateRepo() {
        val now = System.currentTimeMillis()
        val currentStatus = getStatus()

        // Rate limiting updates
        if (currentStatus == Status.Transferring && (now - lastUiUpdate) < UI_UPDATE_LIMIT) return

        if (transferData.direction == Direction.Send) {
            val bps = ((transferData.bytesTransferred - lastBytes) / ((max(
                1, now - lastUiUpdate
            )) / 1000f)).toLong()
            transferSpeed.add(bps)
            transferData = transferData.copy(
                bytesPerSecond = transferSpeed.getMovingAverage()
            )
            lastBytes = transferData.bytesTransferred
        }

        lastUiUpdate = now

        transferData = transferData.copy(status = currentStatus)

        repository.updateTransfer(transferData.remoteUuid, transferData)

        // TODO: Update notification
        // MainService.svc.updateProgress()
    }

    private fun setStatus(s: Status) {
        status.set(s)
        transferData = transferData.copy(status = s)
    }

    private fun getStatus(): Status {
        return status.get()
    }

    suspend fun prepareSend(isDir: Boolean): Transfer = withContext(Dispatchers.IO) {
        val calculatedTopDirNames = ArrayList<String>()
        files.clear()
        dirs.clear()

        for (u in transferData.uris) {
            val name = Utils.getNameFromUri(repository.appContext, u) ?: "unknown"
            calculatedTopDirNames.add(name)

            if (isDir) {
                val docId = DocumentsContract.getTreeDocumentId(u)
                val topdir = MFile().apply {
                    this.name = name
                    this.relPath = name
                    this.isDirectory = true
                }
                dirs.add(topdir)
                resolveTreeUri(u, docId, name)
            } else {
                files.addAll(resolveUri(u))
            }
        }

        val fileCount = (files.size + dirs.size).toLong()
        var singleName = ""
        var singleMime = ""

        if (fileCount == 1L) {
            singleName = calculatedTopDirNames.getOrElse(0) { "" }
            singleMime = repository.appContext.contentResolver.getType(transferData.uris[0]) ?: ""
        }

        setStatus(Status.WaitingPermission)

        transferData = transferData.copy(
            fileCount = fileCount,
            singleFileName = singleName,
            singleMimeType = singleMime,
            totalSize = getTotalSendSize(),
            topDirBaseNames = calculatedTopDirNames,
        )

        updateRepo()
        return@withContext transferData
    }

    // Gets all children of a document and adds them to files and dirs
    private fun resolveTreeUri(rootUri: Uri, docId: String, parent: String) {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(rootUri, docId)
        val items = resolveUri(childrenUri)
        for (f in items) {
            if (f.documentID == null) break
            f.uri = DocumentsContract.buildDocumentUriUsingTree(rootUri, f.documentID)
            f.relPath = parent + "/" + f.name
            if (f.isDirectory) {
                dirs.add(f)
                resolveTreeUri(rootUri, f.documentID!!, f.relPath!!)
            } else {
                files.add(f)
            }
        }
    }

    // Get info about all documents represented by uri
    private fun resolveUri(u: Uri): ArrayList<MFile> {
        val mfs = ArrayList<MFile>()
        try {
            repository.appContext.contentResolver.query(u, null, null, null, null).use { c ->
                if (c == null) {
                    Log.w(TAG, "Could not resolve uri: $u")
                    return mfs
                }
                val idCol = c.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameCol = c.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeCol = c.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val mTimeCol = c.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                val sizeCol = c.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)

                while (c.moveToNext()) {
                    val f = MFile()
                    if (idCol != -1) f.documentID = c.getString(idCol)
                    else Log.w(TAG, "Could not get document ID")

                    f.name = c.getString(nameCol)

                    if (mimeCol != -1) f.mime = c.getString(mimeCol)

                    if (mimeCol == -1 || f.mime == null) {
                        Log.w(TAG, "Could not get MIME type")
                        f.mime = "application/octet-stream"
                    }

                    if (mTimeCol != -1) f.lastMod = c.getLong(mTimeCol)
                    else f.lastMod = -1

                    f.length = c.getLong(sizeCol)
                    f.isDirectory = f.mime!!.endsWith("directory")
                    f.uri = u
                    f.relPath = f.name
                    mfs.add(f)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Could not query resolver: ", e)
        }
        return mfs
    }

    fun generateFileFlow(): Flow<WarpProto.FileChunk> = flow {
        setStatus(Status.Transferring)
        Log.d(TAG, "Sending Flow started. Compression: ${transferData.useCompression}")

        // Reset counters
        transferData = transferData.copy(bytesTransferred = 0)
        lastBytes = 0
        updateRepo()

        // Acquire WakeLock via MainService (or Repository)
        // MainService.svc.acquireWakeLock() // TODO

        var i = 0
        var iDir = 0
        var inputStream: InputStream? = null
        val chunkBuffer = ByteArray(CHUNK_SIZE)
        var firstChunkOfFile = true

        try {
            // 1. Send Directories
            while (iDir < dirs.size && currentCoroutineContext().isActive) {
                val dir = dirs[iDir]
                val fc = WarpProto.FileChunk.newBuilder().setRelativePath(dir.relPath)
                    .setFileType(FileType.Directory.value).setFileMode(0x1ED) // 0755
                    .build()
                emit(fc)
                iDir++
            }

            // 2. Send Files
            while (i < files.size && currentCoroutineContext().isActive) {
                if (inputStream == null) {
                    inputStream =
                        repository.appContext.contentResolver.openInputStream(files[i].uri!!)
                    firstChunkOfFile = true
                }

                val read = inputStream!!.read(chunkBuffer)

                // End of current file
                if (read < 1) {
                    inputStream.close()
                    inputStream = null
                    i++
                    continue
                }

                // Prepare metadata for first chunk
                var fileTime = WarpProto.FileTime.getDefaultInstance()
                if (firstChunkOfFile) {
                    firstChunkOfFile = false
                    val lastMod = files[i].lastMod
                    if (lastMod > 0) {
                        fileTime = WarpProto.FileTime.newBuilder().setMtime(lastMod / 1000)
                            .setMtimeUsec((lastMod % 1000).toInt() * 1000).build()
                    }
                }

                // Compress if needed
                var dataToSend = chunkBuffer
                var dataLen = read
                if (transferData.useCompression) {
                    dataToSend = ZlibCompressor.compress(chunkBuffer, read, DEFAULT_COMPRESSION)
                    dataLen = dataToSend.size
                }

                // Emit Chunk
                val fc = WarpProto.FileChunk.newBuilder().setRelativePath(files[i].relPath)
                    .setFileType(FileType.File.value)
                    .setChunk(ByteString.copyFrom(dataToSend, 0, dataLen))
                    .setFileMode(0x1A4) // 0644
                    .setTime(fileTime).build()

                emit(fc)

                // Update Progress
                transferData =
                    transferData.copy(bytesTransferred = transferData.bytesTransferred + read)
                updateRepo()
            }

            setStatus(Status.Finished)
            unpersistUris()
            updateRepo()

        } catch (e: FileNotFoundException) {
            Log.e(TAG, "File not found during send", e)
            val err = Error.FileNotFound(e.message ?: "Unknown file")
            setStatus(Status.Failed(err, false))
            updateRepo()
            // Re-throw to cancel gRPC stream
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error sending files", e)
            val err = Error.Generic(e.message ?: "Unknown Error")
            setStatus(Status.Failed(err, false))
            updateRepo()
            throw e
        } finally {
            inputStream?.close()
            // MainService.svc.releaseWakeLock() // TODO
        }
    }.flowOn(Dispatchers.IO)

    suspend fun processFileFlow(chunkFlow: Flow<WarpProto.FileChunk>) =
        withContext(Dispatchers.IO) {
            Log.i(TAG, "Starting Receive Flow. Compression: ${transferData.useCompression}")
            setStatus(Status.Transferring)
            receivedPaths.clear()
            updateRepo()

            // MainService.svc.acquireWakeLock() // TODO

            try {
                chunkFlow.collect { chunk ->
                    if (!processSingleChunk(chunk)) {
                        throw Exception("Processing failed or cancelled")
                    }
                }

                finishReceive()

            } catch (e: Exception) {
                Log.e(TAG, "Receive flow error", e)
                if (getStatus() == Status.Transferring) {
                    failReceive(Error.ConnectionLost(e.message))
                }
            } finally {
                // MainService.svc.releaseWakeLock() // TODO
            }
        }

    private fun processSingleChunk(chunk: WarpProto.FileChunk): Boolean {
        var chunkSize: Long = 0

        if (chunk.relativePath != currentRelativePath) {
            closeStream()
            if (currentLastMod != -1L) {
                setLastModified()
                currentLastMod = -1
            }

            finishSafeOverwrite()

            currentRelativePath = chunk.relativePath
            if (repository.server.get().downloadDirUri.isNullOrEmpty()) {
                failReceive(Error.DownloadDirectoryNotSet)
                return false
            }

            val sanitizedName = currentRelativePath!!.replace("[\\\\<>*|?:\"]".toRegex(), "_")

            when (chunk.fileType) {
                FileType.Directory.value -> {
                    createDirectory(sanitizedName)
                }

                FileType.Symlink.value -> {
                    errorList.add("Symlinks not supported: $sanitizedName")
                }

                else -> {
                    // New File
                    if (chunk.hasTime()) {
                        val ft = chunk.time
                        currentLastMod = ft.mtime * 1000 + ft.mtimeUsec / 1000
                    }
                    try {
                        currentStream = openFileStream(sanitizedName)
                        var data = chunk.chunk.toByteArray()
                        if (transferData.useCompression) {
                            data = ZlibCompressor.decompress(data)
                        }
                        currentStream!!.write(data)
                        chunkSize = data.size.toLong()
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to open file: $currentRelativePath", e)
                        failReceive(Error.PermissionDenied(currentRelativePath))
                        return false
                    }
                }
            }
        } else {
            try {
                var data = chunk.chunk.toByteArray()
                if (transferData.useCompression) {
                    data = ZlibCompressor.decompress(data)
                }
                currentStream!!.write(data)
                chunkSize = data.size.toLong()
            } catch (e: Exception) {
                Log.e(TAG, "Write error: $currentRelativePath", e)
                failReceive(Error.Generic("Write error: ${e.message}"))
                return false
            }
        }

        transferData =
            transferData.copy(bytesTransferred = transferData.bytesTransferred + chunkSize)

        val now = System.currentTimeMillis()
        val bps = (chunkSize / (max(1, now - lastMillis) / 1000f)).toLong()
        transferSpeed.add(bps)
        transferData = transferData.copy(bytesPerSecond = transferSpeed.getMovingAverage())
        lastMillis = now

        updateRepo()

        return getStatus() == Status.Transferring
    }

    private fun stopSending() {
        cancelled = true
    }

    private fun unpersistUris() {
        val parsedUris = transferData.uris
        for (u in parsedUris) {
            repository.appContext.contentResolver.releasePersistableUriPermission(
                u, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
    }

    private fun getTotalSendSize(): Long {
        var size: Long = 0
        for (f in files) {
            size += f.length
        }
        return size
    }

    fun prepareReceive() {
        if (BuildConfig.DEBUG && transferData.direction != Direction.Receive) {
            throw AssertionError("Assertion failed")
        }
        if (repository.server.get().allowOverwrite) {
            for (file in transferData.topDirBaseNames) {
                if (checkWillOverwrite(file)) {
                    transferData = transferData.copy(overwriteWarning = true)
                    updateRepo()
                    break
                }
            }
        }

        val autoAccept = repository.prefs.autoAccept

//        if (repository.prefs.notifyIncoming && !autoAccept) {
//            val intent = Intent(repository.appContext, MainActivity::class.java)
//            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
//            intent.putExtra("remote", transferData.remoteUuid)
//            val immutable = PendingIntent.FLAG_IMMUTABLE
//            val pendingIntent = PendingIntent.getActivity(
//                repository.appContext, MainService.notifId, intent, immutable
//            )
//            val notifSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
//
//            val remoteName =
//                repository.remoteListState.value.find { it.uuid == transferData.remoteUuid }?.displayName
//                    ?: ""
//
//            NotificationCompat.Builder(repository.appContext, MainService.CHANNEL_INCOMING)
//                .setContentTitle(
//                    repository.appContext.getString(
//                        R.string.incoming_transfer, remoteName
//                    )
//                ).setContentText(
//                    if (transferData.fileCount == 1L) transferData.singleFileName else repository.appContext.getString(
//                        R.string.num_files, transferData.fileCount
//                    )
//                ).setSmallIcon(android.R.drawable.stat_sys_download_done)
//                .setPriority(NotificationCompat.PRIORITY_HIGH).setSound(notifSound)
//                .setContentIntent(pendingIntent).setAutoCancel(true).build()
////            MainService.svc.notificationMgr!!.notify(MainService.notifId++, notification) TODO: Re-enable notification
//        }
        if (autoAccept) this.startReceive()
    }

    fun startReceive() {
        Log.i(TAG, "Transfer accepted, compression ${transferData.useCompression}")
        setStatus(Status.Transferring)
        actualStartTime = System.currentTimeMillis()
        receivedPaths.clear()
        updateRepo()

        remotesManager.getWorker(transferData.remoteUuid)?.startReceiveTransfer(transferData)

        Log.i(TAG, "Acquiring wake lock for " + MainService.WAKELOCK_TIMEOUT + " min")
//        MainService.svc.wakeLock?.acquire(MainService.WAKELOCK_TIMEOUT * 60 * 1000L) TODO: Re-enable wake lock
    }

    fun declineTransfer() {
        Log.i(TAG, "Transfer declined")
        val worker = remotesManager.getWorker(transferData.remoteUuid)
        if (worker != null) worker.declineTransfer(transferData)
        else Log.w(TAG, "Transfer was from an unknown remote")
        makeDeclined()
    }

    fun finishReceive() {
        Log.d(TAG, "Finalizing transfer")
        if (errorList.isNotEmpty()) setStatus(Status.FinishedWithErrors(errorList.map {
            Error.Generic(
                it
            )
        }))
        else setStatus(Status.Finished)

        closeStream()

        if (currentLastMod > 0) setLastModified()

        finishSafeOverwrite()

        if (repository.prefs.downloadDirUri?.startsWith("content:") == false) MediaScannerConnection.scanFile(
            repository.appContext, receivedPaths.toTypedArray(), null, null
        )
        updateRepo()
    }

    private fun stopReceiving() {
        Log.v(TAG, "Stopping receiving")
        closeStream()
        //Delete incomplete file
        try {
            if (repository.prefs.downloadDirUri!!.startsWith("content:")) {
                val f = DocumentFile.fromSingleUri(repository.appContext, currentUri!!)
                f?.delete()
            } else {
                currentFile!!.delete()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not delete incomplete file", e)
        }
    }

    private fun failReceive(specificError: Error? = null) {
        //Don't overwrite other reason for stopping
        if (getStatus() == Status.Transferring) {
            Log.v(TAG, "Receiving failed")
            setStatus(Status.Failed(specificError ?: Error.Generic("Unknown"), false))
            stop(true) //Calls stopReceiving for us
        }
    }

    private fun closeStream() {
        if (currentStream != null) {
            try {
                currentStream!!.close()
                currentStream = null
            } catch (_: Exception) {
            }
        }
    }

    private fun setLastModified() {
        //This is apparently not possible with SAF
        if (repository.prefs.downloadDirUri?.startsWith("content:") == false) {
            Log.d(TAG, "Setting lastMod: $currentLastMod")
            currentFile!!.setLastModified(currentLastMod)
        }
    }

    private fun checkWillOverwrite(relPath: String): Boolean {
        if (repository.prefs.downloadDirUri!!.startsWith("content:")) {
            val treeRoot = repository.prefs.downloadDirUri!!.toUri()
            return Utils.pathExistsInTree(repository.appContext, treeRoot, relPath)
        } else {
            return File(repository.prefs.downloadDirUri, relPath).exists()
        }
    }

    private fun handleFileExists(f: File): File {
        var file = f
        Log.d(TAG, "File exists: " + file.absolutePath)
        if (repository.prefs.allowOverwrite) {
            file = File(file.parentFile, file.name + TMP_FILE_SUFFIX)
            Log.v(TAG, "Writing to temp file $file")
            safeOverwriteFlag = true
        } else {
            val name = file.parent!! + "/" + Files.getNameWithoutExtension(file.absolutePath)
            val ext = Files.getFileExtension(file.absolutePath)
            var i = 1
            while (file.exists()) file = File("$name(${i++}).$ext")
            Log.d(TAG, "Renamed to " + file.absolutePath)
        }
        return file
    }

    private fun handleUriExists(path: String): String {
        var p = path
        val root = repository.prefs.downloadDirUri!!.toUri()
        val f = Utils.getChildFromTree(repository.appContext, root, p)
        Log.d(TAG, "File exists: " + f.uri)
        if (repository.prefs.allowOverwrite) {
            Log.v(TAG, "Overwriting")
            f.delete()
        } else {
            val dir = p.take(p.lastIndexOf("/") + 1)
            val fileName = p.substring(p.lastIndexOf("/") + 1)

            var name = fileName
            var ext = ""
            if (fileName.contains(".")) {
                name = fileName.substringBefore(".")
                ext = fileName.substringAfter(".")
            }
            var i = 1
            while (Utils.pathExistsInTree(repository.appContext, root, p)) {
                p = "$dir$name($i)$ext"
                i++
            }
            Log.d(TAG, "Renamed to $p")
        }
        return p
    }

    private fun createDirectory(path: String) {
        if (repository.prefs.downloadDirUri!!.startsWith("content:")) {
            val rootUri = repository.prefs.downloadDirUri!!.toUri()
            val root = DocumentFile.fromTreeUri(repository.appContext, rootUri)
            createDirectories(root, path, null) // Note: .. segment is created as (invalid)
        } else {
            val dir = File(repository.prefs.downloadDirUri, path)
            if (!validateFile(dir)) throw IllegalArgumentException("The dir path leads outside download dir")
            if (!dir.mkdirs()) {
                errorList.add("Failed to create directory $path")
                Log.e(TAG, "Failed to create directory $path")
            }
        }
    }

    private fun createDirectories(parent: DocumentFile?, path: String, done: String?) {
        var dir = path
        var rest: String? = null
        if (path.contains("/")) {
            dir = path.substringBefore("/")
            rest = path.substring(path.indexOf("/") + 1)
        }
        val absDir =
            if (done == null) dir else "$done/$dir" //Path from rootUri - just to check existence
        var newDir = DocumentFile.fromTreeUri(
            repository.appContext,
            Utils.getChildUri(repository.server.get().downloadDirUri!!.toUri(), absDir)
        )
        if (newDir?.exists() == false) {
            newDir = parent!!.createDirectory(dir)
            if (newDir == null) {
                errorList.add("Failed to create directory $absDir")
                Log.e(TAG, "Failed to create directory $absDir")
                return
            }
        }
        if (rest != null) createDirectories(newDir, rest, absDir)
    }

    @Throws(FileNotFoundException::class)
    private fun openFileStream(fileName: String): OutputStream {
        var fName = fileName
        if (repository.server.get().downloadDirUri!!.startsWith("content:")) {
            val rootUri = repository.prefs.downloadDirUri!!.toUri()
            val root = DocumentFile.fromTreeUri(repository.appContext, rootUri)
            if (Utils.pathExistsInTree(repository.appContext, rootUri, fName)) {
                fName = handleUriExists(fName)
            }
            //Get parent - createFile will substitute / with _ and checks if parent is descendant of tree root
            var parent = root
            if (fName.contains("/")) {
                val parentRelPath = fName.take(fName.lastIndexOf("/"))
                fName = fName.substring(fName.lastIndexOf("/") + 1)
                val dirUri = Utils.getChildUri(rootUri, parentRelPath)
                parent = DocumentFile.fromTreeUri(repository.appContext, dirUri)
            }
            //Create file
            val mime = guessMimeType(fName)
            val file = parent!!.createFile(mime, fName)
            currentUri = file!!.uri
            return repository.appContext.contentResolver.openOutputStream(currentUri!!)!!
        } else {
            currentFile = File(repository.server.get().downloadDirUri, fName)
            if (currentFile!!.exists()) {
                currentFile = handleFileExists(currentFile!!)
            }
            if (!validateFile(currentFile!!)) throw IllegalArgumentException("The file name leads to a file outside download dir")


            if (!safeOverwriteFlag) {
                receivedPaths.add(currentFile!!.absolutePath)
            } else {
                // If it is a temp file, we want to scan the final name later, not the .warpinatortmp
                val realPath = currentFile!!.absolutePath.removeSuffix(TMP_FILE_SUFFIX)
                receivedPaths.add(realPath)
            }


            return FileOutputStream(currentFile, false)
        }
    }

    private fun finishSafeOverwrite() {
        if (safeOverwriteFlag) {
            safeOverwriteFlag = false
            val tempPath = currentFile?.path ?: return

            // Sanity check
            if (!tempPath.endsWith(TMP_FILE_SUFFIX)) return

            val targetPath = tempPath.dropLast(TMP_FILE_SUFFIX.length)
            Log.d(TAG, "Renaming tempfile to $targetPath")

            try {
                val targetFile = File(targetPath)
                val tempFileObj = File(tempPath)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    java.nio.file.Files.move(
                        tempFileObj.toPath(),
                        java.nio.file.Paths.get(targetPath),
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE
                    )
                } else {
                    // Fallback for older Android versions
                    // Delete target if it exists
                    if (targetFile.exists()) targetFile.delete()

                    if (!tempFileObj.renameTo(targetFile)) {
                        throw IOException("Could not rename temp file to target name")
                    }
                }
            } catch (e: IOException) {
                Log.e(TAG, "Could not replace target file with temp file", e)
                errorList.add("Failed to overwrite $currentRelativePath")
            }
        }
    }

    private fun validateFile(f: File): Boolean {
        var res = false
        try {
            res = (f.canonicalPath + "/").startsWith(repository.server.get().downloadDirUri!!)
        } catch (e: Exception) {
            Log.w(
                TAG, "Could not resolve canonical path for " + f.absolutePath + ": " + e.message
            )
        }
        return res
    }

    private fun guessMimeType(name: String): String {
        //We don't care about knowing the EXACT mime type
        //This is only to prevent fail on some devices that reject empty mime type
        var mime = URLConnection.guessContentTypeFromName(name)
        if (mime == null) mime = "application/octet-stream"
        return mime
    }

    class MFile {
        var documentID: String? = null
        var name: String? = null
        var mime: String? = null
        var relPath: String? = null
        var uri: Uri? = null
        var length: Long = 0
        var lastMod: Long = 0
        var isDirectory: Boolean = false
    }

    class TransferSpeed(private val historyLength: Int) {
        private val history: LongArray = LongArray(historyLength)
        private var idx = 0
        private var count = 0

        fun add(bps: Long) {
            history[idx] = bps
            idx = (idx + 1) % historyLength
            if (count < historyLength) count++
        }

        fun getMovingAverage(): Long {
            if (count == 0) return 0
            else if (count == 1) return history[0]

            var sum: Long = 0
            for (i in 0 until count) sum += history[i]
            return sum / count
        }
    }

    companion object {
        private const val TAG = "TRANSFER"
        private const val CHUNK_SIZE = 1024 * 512 //512 kB
        private const val UI_UPDATE_LIMIT: Long = 250
        private const val TMP_FILE_SUFFIX = ".warpinatortmp"
    }
}