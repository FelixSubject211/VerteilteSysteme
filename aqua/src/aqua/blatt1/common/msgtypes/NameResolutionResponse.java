package aqua.blatt1.common.msgtypes;

import java.io.Serializable;
import java.net.InetSocketAddress;

public class NameResolutionResponse implements Serializable {
    private final InetSocketAddress address;
    private final String id;

    public NameResolutionResponse(InetSocketAddress address, String id) {
        this.address = address;
        this.id = id;
    }

    public InetSocketAddress getAddress() {
        return address;
    }

    public String getId() {
        return id;
    }
}
