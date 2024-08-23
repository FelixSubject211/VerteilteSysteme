package aqua.blatt1.common.msgtypes;

import java.io.Serializable;
import java.net.InetSocketAddress;

@SuppressWarnings("serial")
public final class RegisterResponse implements Serializable {
	private final String id;
	private final InetSocketAddress left;
	private final InetSocketAddress right;
	private final boolean hasToken;
	private final int leaseDuration;

	public RegisterResponse(
			String id,
			InetSocketAddress left,
			InetSocketAddress right,
			boolean hasToken,
			int leaseDuration
	) {
		this.id = id;
		this.left = left;
		this.right = right;
		this.hasToken = hasToken;
		this.leaseDuration = leaseDuration;
	}

	public String getId() {
		return id;
	}

	public InetSocketAddress getLeft() {
		return left;
	}

	public InetSocketAddress getRight() {
		return right;
	}

	public boolean hasToken() { return hasToken; }

	public int getLeaseDuration() { return leaseDuration; }
}
