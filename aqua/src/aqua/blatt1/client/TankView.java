package aqua.blatt1.client;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Observable;
import java.util.Observer;

import javax.swing.*;

import aqua.blatt1.common.FishModel;

public class TankView extends JPanel implements Observer {
	private final TankModel tankModel;
	private final FishView fishView;
	private final Runnable repaintRunnable;
	private final JLabel statusLabel;

	public TankView(final TankModel tankModel) {
		this.tankModel = tankModel;
		fishView = new FishView();

		statusLabel = new JLabel("Snapshot count: 0");
		add(statusLabel); // JLabel zur TankView hinzufügen

		repaintRunnable = new Runnable() {
			@Override
			public void run() {
				repaint();
			}
		};

		setPreferredSize(new Dimension(TankModel.WIDTH, TankModel.HEIGHT));
		setBackground(new Color(175, 200, 235));

		addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				tankModel.newFish(e.getX(), e.getY());
			}
		});
	}

	private void drawBorders(Graphics2D g2d) {
		int thickness = 5;

		for (int i = 0; i < thickness; i++) {
			g2d.drawLine(i, 0, i, TankModel.HEIGHT);
		}

		for (int i = 0; i < thickness; i++) {
			g2d.drawLine(TankModel.WIDTH - 1 - i, 0, TankModel.WIDTH - 1 - i, TankModel.HEIGHT);
		}
	}

	private void doDrawing(Graphics g) {
		Graphics2D g2d = (Graphics2D) g;

		if (!tankModel.hasToken) {
			drawBorders(g2d);
		}

		for (FishModel fishModel : tankModel) {
			g2d.drawImage(fishView.getImage(fishModel), fishModel.getX(), fishModel.getY(), null);
			g2d.drawString(fishModel.getId(), fishModel.getX(), fishModel.getY());
		}

	}

	@Override
	public void paintComponent(Graphics g) {
		super.paintComponent(g);
		doDrawing(g);
	}

	@Override
	public void update(Observable o, Object arg) {
		SwingUtilities.invokeLater(repaintRunnable);
		if (arg instanceof String) {
			statusLabel.setText((String) arg); // Update the JLabel with the snapshot count
		}
	}
}
