package aqua.blatt1.client;

import java.net.InetSocketAddress;

import aqua.blatt1.common.SecureEndpoint;
import aqua.blatt1.common.msgtypes.*;
import messaging.Endpoint;
import messaging.Message;
import aqua.blatt1.common.FishModel;
import aqua.blatt1.common.Properties;

public class ClientCommunicator {
	private final Endpoint endpoint;

	public ClientCommunicator() {
		endpoint = new SecureEndpoint();
	}

	public class ClientForwarder {
		private final InetSocketAddress broker;

		private ClientForwarder() {
			this.broker = new InetSocketAddress(Properties.HOST, Properties.PORT);
		}

		public void register() {
			endpoint.send(broker, new RegisterRequest());
		}

		public void deregister(String id) {
			endpoint.send(broker, new DeregisterRequest(id));
		}

		public void handOff(FishModel fish, InetSocketAddress address) {
			endpoint.send(address, new HandoffRequest(fish));
		}

		public void sendToken(TankModel tankModel) {
			endpoint.send(tankModel.left, new Token());
		}

		public void sendSnapshotMarker(InetSocketAddress address) {
			endpoint.send(address, new SnapshotMarker());
		}

		public void sendSnapshotToken(InetSocketAddress address, SnapshotToken snapshotToken) {
			endpoint.send(address, snapshotToken);
		}

		public void sendLocationRequest(InetSocketAddress address, LocationRequest locationRequest) {
			endpoint.send(address, locationRequest);
		}

		public void sendNameNameResolutionRequest(NameResolutionRequest nameResolutionRequest) {
			endpoint.send(broker, nameResolutionRequest);
		}
	}

	public class ClientReceiver extends Thread {
		private final TankModel tankModel;

		private ClientReceiver(TankModel tankModel) {
			this.tankModel = tankModel;
		}

		@Override
		public void run() {
			while (!isInterrupted()) {
				Message msg = endpoint.blockingReceive();

				if (msg.getPayload() instanceof RegisterResponse) {
					tankModel.left = ((RegisterResponse) msg.getPayload()).getLeft();
					tankModel.right = ((RegisterResponse) msg.getPayload()).getRight();
					tankModel.onRegistration(((RegisterResponse) msg.getPayload()));

					if (((RegisterResponse) msg.getPayload()).hasToken()) {
						tankModel.receiveToken();
					}
				}

				if (msg.getPayload() instanceof HandoffRequest)
					tankModel.receiveFish(((HandoffRequest) msg.getPayload()).getFish());

				if (msg.getPayload() instanceof NeighborUpdate neighborUpdate) {
                    if (neighborUpdate.getLeftOrNull() != null) {
						tankModel.left = neighborUpdate.getLeftOrNull();
					}
					if (neighborUpdate.getRightOrNull() != null) {
						tankModel.right = neighborUpdate.getRightOrNull();
					}
				}

				if(msg.getPayload() instanceof Token) {
					tankModel.receiveToken();
				}

				if (msg.getPayload() instanceof SnapshotMarker) {
					tankModel.receiveSnapshotMarker(msg.getSender());
				}

				if (msg.getPayload() instanceof SnapshotToken token) {
					tankModel.receiveSnapshotToken(token);
				}

				if (msg.getPayload() instanceof LocationRequest request) {
					tankModel.locateFishGlobally(request.getFishId());
				}

				if (msg.getPayload() instanceof NameResolutionResponse response) {
					tankModel.receiveNameResolutionResponse(response);
				}
			}
			System.out.println("Receiver stopped.");
		}
	}

	public ClientForwarder newClientForwarder() {
		return new ClientForwarder();
	}

	public ClientReceiver newClientReceiver(TankModel tankModel) {
		return new ClientReceiver(tankModel);
	}
}
