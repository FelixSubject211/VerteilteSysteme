package aqua.blatt1.client;

import java.net.InetSocketAddress;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.Timer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import aqua.blatt1.broker.RemoteBroker;
import aqua.blatt1.common.Direction;
import aqua.blatt1.common.FishModel;
import aqua.blatt1.common.msgtypes.*;

public class TankModel extends Observable implements Iterable<FishModel>, RemoteClient {
	public static final int WIDTH = 600;
	public static final int HEIGHT = 350;
	protected static final int MAX_FISHIES = 5;
	protected static final Random rand = new Random();
	protected volatile String id;
	protected final Set<FishModel> fishies;
	protected int fishCounter = 0;
	protected RemoteClient left = null;
	protected RemoteClient right = null;
	protected boolean hasToken = false;
	protected final Timer timer;
	protected final RemoteBroker broker;
	public RemoteClient client;

	public TankModel(RemoteBroker broker) throws RemoteException {
		this.fishies = Collections.newSetFromMap(new ConcurrentHashMap<FishModel, Boolean>());
		this.timer = new Timer();
		this.broker = broker;
		this.client = (RemoteClient) UnicastRemoteObject.exportObject(this, 0);
	}

	public synchronized void newFish(int x, int y) {
		if (fishies.size() < MAX_FISHIES) {
			x = x > WIDTH - FishModel.getXSize() - 1 ? WIDTH - FishModel.getXSize() - 1 : x;
			y = y > HEIGHT - FishModel.getYSize() ? HEIGHT - FishModel.getYSize() : y;

			FishModel fish = new FishModel("fish" + (++fishCounter) + "@" + getId(), x, y,
					rand.nextBoolean() ? Direction.LEFT : Direction.RIGHT);

			fishies.add(fish);
		}
	}

	public String getId() {
		return id;
	}

	public synchronized int getFishCounter() {
		return fishCounter;
	}

	public synchronized Iterator<FishModel> iterator() {
		return fishies.iterator();
	}

	private synchronized void updateFishies() throws RemoteException {
		for (Iterator<FishModel> it = iterator(); it.hasNext();) {
			FishModel fish = it.next();

			fish.update();

			if (fish.hitsEdge()) {
				if (hasToken) {
					switch (fish.getDirection()) {
						case LEFT:
							left.handoffRequest(fish);
							break;
						case RIGHT:
							right.handoffRequest(fish);
							break;
					}
				} else {
					fish.reverse();
				}
			}

			if (fish.disappears())
				it.remove();
		}
	}

	private synchronized void update() throws RemoteException {
		updateFishies();
		setChanged();
		notifyObservers();
	}

	protected void run() {
		try {
			broker.registerRequest(client);

			while (!Thread.currentThread().isInterrupted()) {
				update();
				TimeUnit.MILLISECONDS.sleep(10);
			}
		} catch (InterruptedException | RemoteException consumed) {
			// allow method to terminate
		}
	}

	public synchronized void finish() throws RemoteException {
		broker.deregisterRequest(client);
		try {
			timer.cancel();
		} catch (IllegalStateException ignored) {}
		if (hasToken) {
			left.token();
		}
	}

	private RecordingState recordingState = RecordingState.IDLE;
	private int recordingFishCounter = 0;
	private boolean isInitiator = false;

	public synchronized void initiateSnapshot() throws RemoteException {
		isInitiator = true;

		recordingFishCounter = (int) fishies.stream()
				.filter(fish -> !fish.isDeparting())
				.count();

		recordingState = RecordingState.BOTH;

		left.snapshotMarker(client);
		right.snapshotMarker(client);
	}

	Map<String, RemoteClient> fishIdToAddress = new HashMap<>();

	@Override
	public void registerResponse(String id, RemoteClient left, RemoteClient right, boolean hasToken, int leaseDuration) throws RemoteException {
		if (this.id == null) {
			this.id = id;
			newFish(WIDTH - FishModel.getXSize(), rand.nextInt(HEIGHT - FishModel.getYSize()));
		}

		this.left = left;
		this.right = right;

		if (hasToken && !this.hasToken) {
			token();
		}

		timer.schedule(new TimerTask() {
			@Override
			public void run() {
				try {
					broker.registerRequest(client);
				} catch (RemoteException e) {
					throw new RuntimeException(e);
				}
			}
		}, leaseDuration * 1000L);
	}

	@Override
	public void handoffRequest(FishModel fish) throws RemoteException {
		if (recordingState != RecordingState.IDLE) {
			recordingFishCounter++;
		}

		if (!fish.getTankId().equals(id)) {
			broker.nameResolutionRequest(client, fish.getTankId(), fish.getId());
		} else {
			fishIdToAddress.remove(fish.getId());
		}

		fish.setToStart();
		fishies.add(fish);
	}

	@Override
	public void token() throws RemoteException {
		hasToken = true;

		timer.schedule(new TimerTask() {
			@Override
			public void run() {
				synchronized (TankModel.this) {
					hasToken = false;
					try {
						left.token();
					} catch (RemoteException ignored) {}
				}
			}
		}, 2000);
	}

	@Override
	public void neighborUpdate(RemoteClient leftOrNull, RemoteClient rightOrNull) throws RemoteException {
		if (leftOrNull != null) {
			this.left = leftOrNull;
		}

		if (rightOrNull != null) {
			this.right = rightOrNull;
		}
	}

	@Override
	public void snapshotMarker(RemoteClient sender) throws RemoteException {
		System.out.println(sender);
		if(isInitiator) {
			switch (recordingState) {
				case BOTH:
					if (sender.equals(left)) {
						recordingState = RecordingState.RIGHT;
					} else {
						recordingState = RecordingState.LEFT;
					}
					break;
				case LEFT:
					if(sender.equals(left)) {
						recordingState = RecordingState.IDLE;
						left.snapshotToken(0);
					}
					break;
				case RIGHT:
					if(sender.equals(right)) {
						recordingState = RecordingState.IDLE;
						left.snapshotToken(0);
					}
					break;
			}
		} else {
			switch (recordingState) {
				case IDLE:
					recordingFishCounter = (int) fishies.stream()
							.filter(fish -> !fish.isDeparting())
							.count();
					if(sender.equals(left)) {
						recordingState = RecordingState.RIGHT;
						right.snapshotMarker(client);
					} else {
						recordingState = RecordingState.LEFT;
						left.snapshotMarker(client);
					}
					break;
				case LEFT:
					if(sender.equals(left)) {
						recordingState = RecordingState.IDLE;
						right.snapshotMarker(client);
					}
				case RIGHT:
					if(sender.equals(right)) {
						recordingState = RecordingState.IDLE;
						left.snapshotMarker(client);
					}
			}
		}
	}

	@Override
	public void snapshotToken(int count) throws RemoteException {
		System.out.println(count);
		if(isInitiator) {
			String message = "Snapshot count: " + (count + recordingFishCounter);
			setChanged();
			notifyObservers(message);
		} else {
			left.snapshotToken(count + recordingFishCounter);
		}
		isInitiator = false;
		recordingFishCounter = 0;
		recordingState = RecordingState.IDLE;
	}

	@Override
	public void locationRequest(String fishId) throws RemoteException {
		RemoteClient address = fishIdToAddress.get(fishId);

		if (address == null) {
			fishies.stream()
					.filter(fish -> fish.getId().equals(fishId))
					.findFirst()
					.ifPresentOrElse(
							FishModel::toggle,
							() -> System.err.println("Error: Fish with ID " + fishId + " not found.")
					);
		} else {
			address.locationRequest(fishId);
		}
	}

	@Override
	public void nameResolutionResponse(RemoteClient address, String id) throws RemoteException {
		fishIdToAddress.put(id, address);
	}
}