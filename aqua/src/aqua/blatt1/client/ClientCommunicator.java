package aqua.blatt1.client;

import java.net.InetSocketAddress;
import aqua.blatt1.common.msgtypes.*;
import messaging.Endpoint;
import messaging.Message;
import aqua.blatt1.common.FishModel;
import aqua.blatt1.common.Properties;

public class ClientCommunicator {
	private final Endpoint endpoint;

	public ClientCommunicator() {
		endpoint = new Endpoint();
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

		public void handOff(FishModel fish, TankModel tankModel) {
			switch (fish.getDirection()) {
				case LEFT:
					endpoint.send(tankModel.left, new HandoffRequest(fish));
					break;
				case RIGHT:
					endpoint.send(tankModel.right, new HandoffRequest(fish));
					break;
			}
		}

		public void sendToken(TankModel tankModel) {
			endpoint.send(tankModel.left, new Token());
		}

		public void sendSnapshotMarker(InetSocketAddress address) {
			endpoint.send(address, new SnapshotMarker());
			System.out.println("send SnapshotMarker to" + address);
		}

		public void sendSnapshotToken(InetSocketAddress address, SnapshotToken snapshotToken) {
			endpoint.send(address, snapshotToken);
			System.out.println("send Token to" + address + " " + snapshotToken.getCount());
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
					tankModel.onRegistration(((RegisterResponse) msg.getPayload()).getId());

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
					System.out.println("rcv from SnapshotMarker " + msg.getSender());
					tankModel.receiveSnapshotMarker(msg.getSender());
				}

				if (msg.getPayload() instanceof SnapshotToken token) {
					System.out.println("rcv from " + msg.getSender() + " " + token.getCount());
					tankModel.receiveSnapshotToken(token);
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
