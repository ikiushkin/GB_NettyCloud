package cloudnetwork.callbacks;

import cloudcommon.resources.FileRepresentation;

import java.util.List;

public interface FileListCallback {
    void callback(List<FileRepresentation> list);
}
