package aqua.blatt1.common.msgtypes;

import java.io.Serializable;
import java.net.InetSocketAddress;

@SuppressWarnings("serial")
public final class RegisterResponse implements Serializable {
	private final String id;
	InetSocketAddress left;
	InetSocketAddress right;
	boolean hasToken;

	public RegisterResponse(
			String id,
			InetSocketAddress left,
			InetSocketAddress right,
			boolean hasToken
	) {
		this.id = id;
		this.left = left;
		this.right = right;
		this.hasToken = hasToken;
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
}
