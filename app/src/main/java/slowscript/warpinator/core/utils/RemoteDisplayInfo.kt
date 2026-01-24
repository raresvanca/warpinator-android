package slowscript.warpinator.core.utils

import slowscript.warpinator.core.model.Remote

data class RemoteDisplayInfo(val title: String, val subtitle: String, val label: String?) {

    companion object {
        fun fromRemote(remote: Remote): RemoteDisplayInfo = fromValues(
            remote.displayName, remote.userName, remote.hostname, remote.address?.hostAddress,
        )

        fun fromValues(
            displayName: String?, userName: String?, hostname: String?, address: String?,
        ): RemoteDisplayInfo {
            val normalizedDisplayName = displayName?.takeIf { it.isNotBlank() }
            val normalizedUserName = userName?.takeIf { it.isNotBlank() }
            val normalizedHostname = hostname?.takeIf { it.isNotBlank() }
            val normalizedAddress = address?.takeIf { it.isNotBlank() }

            val subtitle: String
            var label: String? = null

            val title: String = when {
                normalizedDisplayName != null -> {
                    normalizedDisplayName
                }

                normalizedUserName != null -> {
                    normalizedUserName
                }

                normalizedHostname != null && normalizedAddress != null -> {
                    normalizedHostname
                }

                else -> {
                    "Unknown"
                }
            }

            if (normalizedDisplayName != null) {
                if (normalizedUserName != null) {
                    subtitle = when {
                        normalizedHostname != null -> {
                            label = normalizedAddress
                            "$normalizedUserName@$normalizedHostname"
                        }

                        normalizedAddress != null -> {
                            "$normalizedUserName@$normalizedAddress"
                        }

                        else -> {
                            normalizedUserName
                        }
                    }
                } else {
                    subtitle = when {
                        normalizedHostname != null -> {
                            label = normalizedAddress
                            normalizedHostname
                        }

                        normalizedAddress != null -> {
                            normalizedAddress
                        }

                        else -> {
                            normalizedDisplayName
                        }
                    }
                }
            } else if (normalizedUserName != null) {
                subtitle = when {
                    normalizedHostname != null -> {
                        label = normalizedAddress
                        normalizedHostname
                    }

                    normalizedAddress != null -> {
                        normalizedAddress
                    }

                    else -> {
                        normalizedUserName
                    }
                }
            } else {
                subtitle = when {
                    normalizedHostname != null && normalizedAddress != null -> {
                        normalizedAddress
                    }

                    normalizedHostname != null -> {
                        normalizedHostname
                    }

                    normalizedAddress != null -> {
                        normalizedAddress
                    }

                    else -> {
                        ""
                    }
                }
            }

            return RemoteDisplayInfo(title, subtitle, label)
        }
    }
}