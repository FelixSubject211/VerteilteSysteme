package aqua.blatt1.common.msgtypes;

import java.io.Serializable;

public class NameResolutionRequest implements Serializable {
    private final String tankId;
    private final String id;

    public NameResolutionRequest(String tankId, String id) {
        this.tankId = tankId;
        this.id = id;
    }

    public String getTankId() {
        return tankId;
    }

    public String getId() {
        return id;
    }
}
