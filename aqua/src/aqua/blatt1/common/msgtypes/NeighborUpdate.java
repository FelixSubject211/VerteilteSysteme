package aqua.blatt1.common.msgtypes;

import java.io.Serializable;
import java.net.InetSocketAddress;

public class NeighborUpdate implements Serializable {
    InetSocketAddress left;
    InetSocketAddress right;

    public NeighborUpdate(InetSocketAddress left, InetSocketAddress right) {
        this.left = left;
        this.right = right;
    }

    public InetSocketAddress getLeftOrNull() {
        return left;
    }

    public InetSocketAddress getRightOrNull() {
        return right;
    }
}
