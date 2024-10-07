import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import org.bytedeco.opencv.opencv_core.*;
import org.bytedeco.opencv.global.opencv_imgcodecs;
import org.bytedeco.opencv.opencv_face.LBPHFaceRecognizer;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.opencv.opencv_objdetect.CascadeClassifier;

public class CriminalFaceIdentificationSystem extends JFrame {
	JLabel photoLabel;
	JTable table;
	DefaultTableModel tableModel;
	JButton btnSelectPhoto, btnViewRecords;
	String selectedImagePath;
	ArrayList<String> knownFaceNames = new ArrayList<>();
	ArrayList<Mat> knownFaceEncodings = new ArrayList<>();

	public CriminalFaceIdentificationSystem() {
		// JFrame settings
		setTitle("CFIS - Criminal Face Identification System");
		setSize(1350, 720);
		setLayout(null);
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		getContentPane().setBackground(new Color(56, 34, 115));

		// Labels
		JLabel titleLabel = new JLabel("Criminal Face Identification System", JLabel.CENTER);
		titleLabel.setBounds(0, 0, 1350, 50);
		titleLabel.setFont(new Font("Arial", Font.BOLD, 24));
		titleLabel.setForeground(Color.WHITE);
		titleLabel.setBackground(Color.GRAY);
		titleLabel.setOpaque(true);
		add(titleLabel);

		JLabel selectPhotoLabel = new JLabel("Select Photo to Detect Faces", JLabel.LEFT);
		selectPhotoLabel.setBounds(30, 60, 400, 25);
		selectPhotoLabel.setFont(new Font("Arial", Font.BOLD, 15));
		selectPhotoLabel.setForeground(Color.WHITE);
		add(selectPhotoLabel);

		// Image placeholder
		photoLabel = new JLabel();
		photoLabel.setBounds(90, 110, 400, 400);
		photoLabel.setBorder(BorderFactory.createLineBorder(Color.BLACK));
		add(photoLabel);

		// Table for displaying records
		tableModel = new DefaultTableModel(new Object[]{"Criminal-ID", "Name", "Crime", "Nationality"}, 0);
		table = new JTable(tableModel);
		JScrollPane scrollPane = new JScrollPane(table);
		scrollPane.setBounds(680, 90, 550, 400);
		add(scrollPane);

		// Buttons
		btnSelectPhoto = new JButton("Select Photo");
		btnSelectPhoto.setBounds(200, 550, 200, 30);
		btnSelectPhoto.addActionListener(e -> openFileDialog());
		add(btnSelectPhoto);

		btnViewRecords = new JButton("View Matching Records");
		btnViewRecords.setBounds(165, 620, 250, 40);
		btnViewRecords.setBackground(Color.RED);
		btnViewRecords.setForeground(Color.WHITE);
		btnViewRecords.addActionListener(e -> processImageForMatching());
		add(btnViewRecords);

		loadCriminalData();
	}

	// Load criminal data from the database
	private void loadCriminalData() {
		try (Connection conn = DriverManager.getConnection("jdbc:sqlite:criminal.db")) {
			Statement stmt = conn.createStatement();
			ResultSet rs = stmt.executeQuery("SELECT ID, name FROM people");

			while (rs.next()) {
				knownFaceNames.add(rs.getString("ID"));
				String imgPath = "images/user." + rs.getString("ID") + ".png";
				Mat faceImg = opencv_imgcodecs.imread(imgPath, opencv_imgcodecs.IMREAD_GRAYSCALE);
				knownFaceEncodings.add(faceImg); // Simplified encoding for demo
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	// Open file dialog to select an image
	private void openFileDialog() {
		JFileChooser fileChooser = new JFileChooser();
		fileChooser.setCurrentDirectory(new File(System.getProperty("user.home")));
		int result = fileChooser.showOpenDialog(this);
		if (result == JFileChooser.APPROVE_OPTION) {
			File selectedFile = fileChooser.getSelectedFile();
			selectedImagePath = selectedFile.getAbsolutePath();
			ImageIcon imageIcon = new ImageIcon(new ImageIcon(selectedImagePath).getImage().getScaledInstance(400, 400, Image.SCALE_SMOOTH));
			photoLabel.setIcon(imageIcon);
		}
	}

	// Process the selected image and match against the criminal database
	private void processImageForMatching() {
		if (selectedImagePath == null) {
			JOptionPane.showMessageDialog(this, "Please select an image first.");
			return;
		}

		Mat img = opencv_imgcodecs.imread(selectedImagePath);
		CascadeClassifier faceDetector = new CascadeClassifier("haarcascade_frontalface_default.xml");
		RectVector faces = new RectVector();
		faceDetector.detectMultiScale(img, faces);

		if (faces.size() > 0) {
			// Assume the first face in the image (for demo purposes)
			Rect faceRect = faces.get(0);
			Mat faceImg = new Mat(img, faceRect);
			opencv_imgproc.resize(faceImg, faceImg, new Size(250, 250));

			LBPHFaceRecognizer recognizer = LBPHFaceRecognizer.create();
			recognizer.read("recognizer/training_data.yml");

			int[] label = new int[1];
			double[] confidence = new double[1];
			recognizer.predict(faceImg, label, confidence);

			if (confidence[0] < 100) {
				int id = label[0];
				showMatchingRecord(id, confidence[0]);
			} else {
				JOptionPane.showMessageDialog(this, "No Match Found!");
			}
		} else {
			JOptionPane.showMessageDialog(this, "No face detected in the selected image.");
		}
	}

	// Show matching record details
	private void showMatchingRecord(int id, double confidence) {
		try (Connection conn = DriverManager.getConnection("jdbc:sqlite:criminal.db")) {
			PreparedStatement stmt = conn.prepareStatement("SELECT * FROM people WHERE ID = ?");
			stmt.setInt(1, id);
			ResultSet rs = stmt.executeQuery();

			if (rs.next()) {
				tableModel.setRowCount(0); // Clear the table
				tableModel.addRow(new Object[]{rs.getString("ID"), rs.getString("name"), rs.getString("crime"), rs.getString("nationality")});
				JOptionPane.showMessageDialog(this, "Match Found! Confidence: " + confidence + "%");
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public static void main(String[] args) {
		SwingUtilities.invokeLater(() -> {
			try {
				UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
			} catch (Exception e) {
				e.printStackTrace();
			}
			new CriminalFaceIdentificationSystem().setVisible(true);
		});
	}
}
