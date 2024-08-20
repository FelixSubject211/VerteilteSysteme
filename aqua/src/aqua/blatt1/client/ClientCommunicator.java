package aqua.blatt1.client;

import java.net.InetSocketAddress;
import java.util.function.Consumer;
import java.util.function.Supplier;

import aqua.blatt1.common.msgtypes.*;
import messaging.Endpoint;
import messaging.Message;
import aqua.blatt1.common.FishModel;
import aqua.blatt1.common.Properties;

public class ClientCommunicator {
	private final Endpoint endpoint;
	private InetSocketAddress left = null;
	private InetSocketAddress right = null;

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

		public void handOff(FishModel fish) {
			switch (fish.getDirection()) {
				case LEFT:
					retryUntilNotNull(() -> left, target -> endpoint.send(target, new HandoffRequest(fish)));
					break;
				case RIGHT:
					retryUntilNotNull(() -> right, target -> endpoint.send(target, new HandoffRequest(fish)));
					break;
			}
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

				if (msg.getPayload() instanceof RegisterResponse)
					tankModel.onRegistration(((RegisterResponse) msg.getPayload()).getId());

				if (msg.getPayload() instanceof HandoffRequest)
					tankModel.receiveFish(((HandoffRequest) msg.getPayload()).getFish());

				if (msg.getPayload() instanceof NeighborUpdate neighborUpdate) {
                    if (neighborUpdate.getLeftOrNull() != null) {
						left = neighborUpdate.getLeftOrNull();
					}
					if (neighborUpdate.getRightOrNull() != null) {
						right = neighborUpdate.getRightOrNull();
					}
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


	public static <T> void retryUntilNotNull(Supplier<T> supplier, Consumer<T> action) {
		T value;
		while ((value = supplier.get()) == null) {
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return;
			}
		}
		action.accept(value);
	}
}
