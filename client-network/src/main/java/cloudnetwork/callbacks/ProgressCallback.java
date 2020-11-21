package cloudnetwork.callbacks;

import cloudcommon.services.transfer.Progress;

public interface ProgressCallback {
    void callback(Progress progress);
}
