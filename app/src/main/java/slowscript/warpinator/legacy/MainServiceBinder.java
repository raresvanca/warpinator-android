package slowscript.warpinator.legacy;

import android.os.Binder;

import slowscript.warpinator.core.service.MainService;

public class MainServiceBinder extends Binder {

    private final MainService service;

    public MainServiceBinder(MainService service) {
        this.service = service;
    }

    public MainService getService() {
        return service;
    }
}
