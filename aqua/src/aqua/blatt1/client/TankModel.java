package aqua.blatt1.client;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.Timer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import aqua.blatt1.common.Direction;
import aqua.blatt1.common.FishModel;
import aqua.blatt1.common.msgtypes.LocationRequest;
import aqua.blatt1.common.msgtypes.NameResolutionRequest;
import aqua.blatt1.common.msgtypes.NameResolutionResponse;
import aqua.blatt1.common.msgtypes.SnapshotToken;

public class TankModel extends Observable implements Iterable<FishModel> {

	public static final int WIDTH = 600;
	public static final int HEIGHT = 350;
	protected static final int MAX_FISHIES = 5;
	protected static final Random rand = new Random();
	protected volatile String id;
	protected final Set<FishModel> fishies;
	protected int fishCounter = 0;
	protected final ClientCommunicator.ClientForwarder forwarder;
	protected InetSocketAddress left = null;
	protected InetSocketAddress right = null;
	protected boolean hasToken = false;
	protected final Timer timer;

	public TankModel(ClientCommunicator.ClientForwarder forwarder) {
		this.fishies = Collections.newSetFromMap(new ConcurrentHashMap<FishModel, Boolean>());
		this.forwarder = forwarder;
		this.timer = new Timer();
	}

	synchronized void receiveToken() {
		hasToken = true;

		timer.schedule(new TimerTask() {
			@Override
			public void run() {
				synchronized (TankModel.this) {
					hasToken = false;
					forwarder.sendToken(TankModel.this);
				}
			}
		}, 2000);
	}

	synchronized boolean hasToken() {
		return hasToken;
	}

	synchronized void onRegistration(String id) {
		this.id = id;
		newFish(WIDTH - FishModel.getXSize(), rand.nextInt(HEIGHT - FishModel.getYSize()));
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

	synchronized void receiveFish(FishModel fish) {
		if (recordingState != RecordingState.IDLE) {
			recordingFishCounter++;
		}

		if (!fish.getTankId().equals(id)) {
			forwarder.sendNameNameResolutionRequest(new NameResolutionRequest(fish.getTankId(), fish.getId()));
		} else {
			fishIdToAddress.remove(fish.getId());
		}

		fish.setToStart();
		fishies.add(fish);
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

	private synchronized void updateFishies() {
		for (Iterator<FishModel> it = iterator(); it.hasNext();) {
			FishModel fish = it.next();

			fish.update();

			if (fish.hitsEdge()) {
				if (hasToken) {
					switch (fish.getDirection()) {
						case LEFT:
							forwarder.handOff(fish, left);
							break;
						case RIGHT:
							forwarder.handOff(fish, right);
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

	private synchronized void update() {
		updateFishies();
		setChanged();
		notifyObservers();
	}

	protected void run() {
		forwarder.register();

		try {
			while (!Thread.currentThread().isInterrupted()) {
				update();
				TimeUnit.MILLISECONDS.sleep(10);
			}
		} catch (InterruptedException consumed) {
			// allow method to terminate
		}
	}

	public synchronized void finish() {
		forwarder.deregister(id);
		try {
			timer.cancel();
		} catch (IllegalStateException ignored) {}
		if (hasToken) {
			forwarder.sendToken(TankModel.this);
		}
	}

	private RecordingState recordingState = RecordingState.IDLE;
	private int recordingFishCounter = 0;
	private boolean isInitiator = false;

	public synchronized void initiateSnapshot() {
		isInitiator = true;

		recordingFishCounter = (int) fishies.stream()
				.filter(fish -> !fish.isDeparting())
				.count();

		recordingState = RecordingState.BOTH;

		forwarder.sendSnapshotMarker(left);
		forwarder.sendSnapshotMarker(right);
	}

	public synchronized void receiveSnapshotToken(SnapshotToken snapshotToken) {
		if(isInitiator) {
			String message = "Snapshot count: " + (snapshotToken.getCount() + recordingFishCounter);
			setChanged();
			notifyObservers(message);
		} else {
			forwarder.sendSnapshotToken(left, new SnapshotToken(snapshotToken.getCount() + recordingFishCounter));
		}
		isInitiator = false;
		recordingFishCounter = 0;
		recordingState = RecordingState.IDLE;
	}


	public synchronized void receiveSnapshotMarker(InetSocketAddress sender)  {
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
						forwarder.sendSnapshotToken(left, new SnapshotToken(0));
					}
					break;
				case RIGHT:
					if(sender.equals(right)) {
						recordingState = RecordingState.IDLE;
						forwarder.sendSnapshotToken(left, new SnapshotToken(0));
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
						forwarder.sendSnapshotMarker(right);
					} else {
						recordingState = RecordingState.LEFT;
						forwarder.sendSnapshotMarker(left);
					}
					break;
				case LEFT:
					if(sender.equals(left)) {
						recordingState = RecordingState.IDLE;
						forwarder.sendSnapshotMarker(right);
					}
				case RIGHT:
					if(sender.equals(right)) {
						recordingState = RecordingState.IDLE;
						forwarder.sendSnapshotMarker(left);
					}
			}
		}
	}


	Map<String, InetSocketAddress> fishIdToAddress = new HashMap<>();

	public void locateFishGlobally(String fishId) {
		InetSocketAddress address = fishIdToAddress.get(fishId);

		if (address == null) {
			fishies.stream()
					.filter(fish -> fish.getId().equals(fishId))
					.findFirst()
					.ifPresentOrElse(
							FishModel::toggle,
							() -> System.err.println("Error: Fish with ID " + fishId + " not found.")
					);
		} else {
			forwarder.sendLocationRequest(address, new LocationRequest(fishId));
		}
	}

	public void receiveNameResolutionResponse(NameResolutionResponse nameResolutionResponse) {
		fishIdToAddress.put(nameResolutionResponse.getId(), nameResolutionResponse.getAddress());
	}
}