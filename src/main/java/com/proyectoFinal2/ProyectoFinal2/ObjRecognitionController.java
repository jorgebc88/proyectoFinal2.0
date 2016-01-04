package com.proyectoFinal2.ProyectoFinal2;


import java.io.ByteArrayInputStream;
import java.sql.Blob;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfKeyPoint;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.features2d.FeatureDetector;
import org.opencv.features2d.Features2d;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.imgproc.Moments;
import org.opencv.video.BackgroundSubtractor;
import org.opencv.video.BackgroundSubtractorMOG2;
import org.opencv.video.Video;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.Videoio;

import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.paint.Color;
import javafx.scene.control.ProgressBar;
import javafx.event.EventHandler;
import javafx.scene.input.MouseEvent;

/**
 * The controller associated with the only view of our application. The
 * application logic is implemented here. It handles the button for
 * starting/stopping the camera, the acquired video stream, the relative
 * controls and the image segmentation process.
 * 
 * @author <a href="mailto:luigi.derussis@polito.it">Luigi De Russis</a>
 * @version 1.5 (2015-11-26)
 * @since 1.0 (2015-01-13)
 * 
 */
public class ObjRecognitionController
{
	// FXML camera button
	@FXML
	private Button cameraButton;
	// the FXML area for showing the current frame
	@FXML
	private ImageView originalFrame;
	// the FXML area for showing the mask
	@FXML
	private ImageView maskImage;
	// the FXML area for showing the output of the morphological operations
	@FXML
	private ImageView morphImage;
	// FXML slider for setting HSV ranges
	@FXML
	private Slider hueStart;
	@FXML
	private Slider hueStop;
	@FXML
	private Slider saturationStart;
	@FXML
	private Slider saturationStop;
	@FXML
	private Slider valueStart;
	@FXML
	private Slider valueStop;
	// FXML label to show the current values set with the sliders
	@FXML
	private Label hsvCurrentValues;
	
	@FXML
	private Slider currentTime;
	
	@FXML
	private ProgressBar progressBar;


	
	// a timer for acquiring the video stream
	private ScheduledExecutorService timer;
	// the OpenCV object that performs the video capture
	public VideoCapture capture = new VideoCapture();
	// a flag to change the button behavior
	private boolean cameraActive;
	
	BackgroundSubtractorMOG2 backgroundSubtractor = Video.createBackgroundSubtractorMOG2();
	
	private Mat prevFrame = null;
	
	private int autos = 0, motos = 0;
	
	private Set<Vehicle> vehicleSet= new HashSet<>();

	private double yLinePosition = 0;
	
	// property for object binding
	private ObjectProperty<String> hsvValuesProp;
	private double currentValue = 0;
		
	/**
	 * The action triggered by pushing the button on the GUI
	 */
	@FXML
	private void startCamera()
	{
		// bind a text property with the string containing the current range of
		// HSV values for object detection
		hsvValuesProp = new SimpleObjectProperty<>();
		this.hsvCurrentValues.textProperty().bind(hsvValuesProp);
				
		// set a fixed width for all the image to show and preserve image ratio
		this.imageViewProperties(this.originalFrame, 800);
		this.imageViewProperties(this.maskImage, 400);
		this.imageViewProperties(this.morphImage, 400);
		
		this.currentTime.setOnMouseClicked(new EventHandler<MouseEvent>() {
		    @Override
		    public void handle(MouseEvent t) {
		        //mediaPlayer.seek(Duration.seconds(timeSlider.getValue()));
		    	Slider slider = ((Slider) t.getSource());
		    	slider.setValue(slider.getValue());
		    }
		    });
		
		if (!this.cameraActive)
		{
			// start the video capture
			this.capture.open("F:/Proyecto Final Facultad/Video MAte de Luna 2/Avi/2/MVI_3480.avi");
			//this.capture.open("F:/Proyecto Final Facultad/video.avi");
			//this.capture.open(0);
			this.currentTime.setMax((this.capture.get(Videoio.CAP_PROP_FRAME_COUNT)/this.capture.get(Videoio.CAP_PROP_FPS)) * 1000);
			// is the video stream available?
			if (this.capture.isOpened())
			{
				this.cameraActive = true;
				
				// grab a frame every 33 ms (30 frames/sec)
				this.capture.set(0 , this.currentValue);
				Runnable frameGrabber = new Runnable() {
					
					@Override
					public void run()
					{
						
						Image imageToShow = grabFrame();
						originalFrame.setImage(imageToShow);
					}
				};
				
				this.timer = Executors.newSingleThreadScheduledExecutor();
				this.timer.scheduleAtFixedRate(frameGrabber, 0, 33, TimeUnit.MILLISECONDS);//33
				
				// update the button content
				this.cameraButton.setText("Stop Camera");
			}
			else
			{
				// log the error
				System.err.println("Failed to open the camera connection...");
			}
		}
		else
		{
			// the camera is not active at this point
			this.cameraActive = false;
			// update again the button content
			this.cameraButton.setText("Start Camera");
			// stop the timer
			try
			{
				this.timer.shutdown();
				this.timer.awaitTermination(33, TimeUnit.MILLISECONDS);
			}
			catch (InterruptedException e)
			{
				// log the exception
				System.err.println("Exception in stopping the frame capture, trying to release the camera now... " + e);
			}
			//autos = 0; 
			//vehicleSet.clear();
			this.currentValue = this.capture.get(Videoio.CAP_PROP_POS_MSEC);
			// release the camera
			this.capture.release();
		}
	}
	
	/**
	 * Get a frame from the opened video stream (if any)
	 * 
	 * @return the {@link Image} to show
	 */
	@SuppressWarnings("restriction")
	private Image grabFrame()
	{
		
		
		this.currentTime.setValue(this.capture.get(Videoio.CAP_PROP_POS_MSEC));
		this.progressBar.setProgress((this.capture.get(Videoio.CAP_PROP_POS_FRAMES)/this.capture.get(Videoio.CAP_PROP_FRAME_COUNT)));
		this.capture.set(Videoio.CAP_PROP_POS_MSEC , this.currentTime.getValue());

		// init everything
		Image imageToShow = null;
		Mat frame = new Mat();

		// check if the capture is open
		if (this.capture.isOpened())
		{
			try
			{
				// read the current frame
				this.capture.read(frame);
				this.yLinePosition = frame.height()*0.6;
				// if the frame is not empty, process it
				if (!frame.empty())
				{
					// init
					Mat blurredImage = new Mat();
					Mat grayImage = new Mat();
					Mat mask = new Mat();
					Mat morphOutput = new Mat();
					
					// remove some noise
					Imgproc.blur(frame, blurredImage, new Size(7, 7));
					
					// convert the frame to GRAY
					Imgproc.cvtColor(blurredImage, grayImage, Imgproc.COLOR_BGR2GRAY);
					if(this.prevFrame == null){
						this.prevFrame = grayImage;
					}
					// get thresholding values from the UI
					// remember: H ranges 0-180, S and V range 0-255
					Scalar minValues = new Scalar(198.41269841269883, this.saturationStart.getValue(),
					//Scalar minValues = new Scalar(this.hueStart.getValue(), this.saturationStart.getValue(),
							this.valueStart.getValue());
					Scalar maxValues = new Scalar(5000, this.saturationStop.getValue(),
					//Scalar maxValues = new Scalar(this.hueStop.getValue(), this.saturationStop.getValue(),
							this.valueStop.getValue());
					
					// show the current selected HSV range
					String valuesToPrint = "Hue range: " + minValues.val[0] + "-" + maxValues.val[0]
							+ "\tSaturation range: " + minValues.val[1] + "-" + maxValues.val[1] + "\tValue range: "
							+ minValues.val[2] + "-" + maxValues.val[2];
					this.onFXThread(this.hsvValuesProp, valuesToPrint);
						
					backgroundSubtractor.setDetectShadows(true);
					backgroundSubtractor.setShadowValue(0);
					backgroundSubtractor.apply(grayImage, mask);
					
					// morphological operators
					// dilate with large element, erode with small ones
					Mat dilateElement = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(12, 12));
					Mat erodeElement = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(24, 24));
					//Mat morphKernel = Imgproc.getStructuringElement(Imgproc.MORPH_CROSS, new Size(5,5));

					//Imgproc.morphologyEx(mask, morphOutput, Imgproc.MORPH_CLOSE, erodeElement);
					//Imgproc.morphologyEx(mask, morphOutput, Imgproc.MORPH_OPEN, morphKernel);
					this.onFXThread(this.maskImage.imageProperty(), this.mat2Image(mask));

					Imgproc.erode(mask, morphOutput, erodeElement);

					//this.onFXThread(this.maskImage.imageProperty(), this.mat2Image(morphOutput));

					Imgproc.dilate(mask, morphOutput, dilateElement);
					//Imgproc.dilate(mask, morphOutput, dilateElement);

					Imgproc.blur(morphOutput, morphOutput, new Size(5,5));
					//Imgproc.erode(mask, morphOutput, Imgproc.getStructuringElement(Imgproc.MORPH_CROSS , new Size(2, 2), new Point(1,1)));
					// show the partial output

					Core.inRange(morphOutput, minValues, maxValues, morphOutput);
				
					this.onFXThread(this.morphImage.imageProperty(), this.mat2Image(morphOutput));

					this.prevFrame = grayImage;
					// find the tennis ball(s) contours and show them
					frame = this.findAndDetectVehicles(morphOutput, frame);
					
					// convert the Mat object (OpenCV) to Image (JavaFX)
					imageToShow = mat2Image(frame);

				}
				
			}
			catch (Exception e)
			{
				// log the (full) error
				System.err.print("ERROR");
				e.printStackTrace();
			}
		}
		
		return imageToShow;
	}
	int i = 0;
	/**
	 * Given a binary image containing one or more closed surfaces, use it as a
	 * mask to find and highlight the objects contours
	 * 
	 * @param maskedImage
	 *            the binary image to be used as a mask
	 * @param frame
	 *            the original {@link Mat} image to be used for drawing the
	 *            objects contours
	 * @return the {@link Mat} image with the objects contours framed
	 */
	private Mat findAndDetectVehicles(Mat maskedImage, Mat frame)
	{
		// init
		List<MatOfPoint> contours = new ArrayList<>();
		Mat hierarchy = new Mat();
		
		// find contours
		Imgproc.findContours(maskedImage, contours, hierarchy, Imgproc.RETR_CCOMP, Imgproc.CHAIN_APPROX_SIMPLE);
		Mat contour;
		Rect rect;
		// if any contour exist...
		Imgproc.line(frame, new Point( 0, this.yLinePosition) , new Point( 0.8 * (frame.width()), this.yLinePosition), new Scalar(0, 0, 250));
		if (hierarchy.size().height > 0 && hierarchy.size().width > 0)
		{
			// for each contour, display it in blue
			for (int idx = 0; idx >= 0; idx = (int) hierarchy.get(0, idx)[0])
			{
				contour = contours.get(idx);
				rect = Imgproc.boundingRect(contours.get(idx));
	            if (rect.area() > 2500) 
	            {
	            	
	            	this.defineVehicle(contour, rect, frame.width());
	            	System.out.println("Centro de masa: " + this.calculateMassCenterRectangle(rect));

	            	System.out.println("Autos: " + this.autos);
	            	System.out.println("Tama�o lista: " + this.vehicleSet.size());
	            	
	            	Imgproc.drawMarker(frame, this.calculateMassCenterRectangle(rect), new Scalar(0, 250, 0));
	            	Imgproc.rectangle(frame, new Point(rect.x, rect.y), new Point(rect.x+rect.width, rect.y+rect.height), new Scalar(0, 250, 0));
	            	//Imgproc.drawContours(frame, contours, idx, new Scalar(0, 250, 0));
	            	
	            }
			}
		}
		return frame;
	}
	
	private Point calculateMassCenterRectangle(Rect rect){
		double x, y;
		x = rect.x + (rect.width/2);
		y = rect.y + (rect.height/2);
		return new Point(x, y);
		
	}
	
	private Point calculateMassCenter(Mat contour){
		Moments moment = Imgproc.moments(contour);
    	double cx = moment.m10/moment.m00;
    	double cy = moment.m01/moment.m00;
    	return new Point(cx, cy);
	}
	
	private void defineVehicle (Mat contour, Rect rect, int frameWidth){
		Vehicle vehicleAux;
		boolean isANewVehicle = true;
		if(vehicleSet.isEmpty()){
			vehicleAux = new Vehicle(this.calculateMassCenterRectangle(rect), rect.area(), false, this.isGoingDown(rect, frameWidth));
			vehicleSet.add(vehicleAux);
			return;
		}
		for(Vehicle vehicle : vehicleSet){
			//if(areTheSameSize(vehicle.getVehicleSize(), Imgproc.contourArea(contour))){
				if(this.isMoving(vehicle.getMassCenterLocation(), this.calculateMassCenterRectangle(rect)) ){
					isANewVehicle = false;
					vehicle.setMassCenterLocation(this.calculateMassCenterRectangle(rect));
					vehicle.setVehicleSize(rect.area());
					if(!vehicle.isGoingUp()){
						if(!vehicle.isCounted() && this.shouldBeCounted(rect)){
							vehicle.setCounted(true);
							this.autos++; //modificar!!							
						}
					}
					break;
				}
			//}
		}
		if(isANewVehicle){
			vehicleAux = new Vehicle(this.calculateMassCenterRectangle(rect), rect.area(), false, this.isGoingDown(rect, frameWidth));
			System.out.println(vehicleAux.toString());
			vehicleSet.add(vehicleAux);
		}
	}
	
	private boolean isGoingDown(Rect rect, int frameWidth){
		if(this.calculateMassCenterRectangle(rect).x < frameWidth * 0.7){
			return true;
		}
		return false;
	}
	
	private boolean shouldBeCounted(Rect rect){
		double newYPosition = (this.calculateMassCenterRectangle(rect)).y;
		if(newYPosition > this.yLinePosition){
			return true;
		}
		return false;
	}
	
	
	private boolean isMoving(Point oldCalculateMassCenter, Point newMassCenterLocation) {
		if(newMassCenterLocation.x >= oldCalculateMassCenter.x * 0.80 && newMassCenterLocation.x <= oldCalculateMassCenter.x*1.20){
			if(newMassCenterLocation.y >= oldCalculateMassCenter.y * 0.8 && newMassCenterLocation.y <= oldCalculateMassCenter.y * 1.2 ){
				return true;
			}
		}
		return false;
	}

	private boolean areTheSameSize(double oldVehicle, double  newVehicle){
		if(newVehicle >= oldVehicle*0.80 && newVehicle <= oldVehicle*1.20){
			return true;
		}
		return false;
		
	}
	
	/**
	 * Set typical {@link ImageView} properties: a fixed width and the
	 * information to preserve the original image ration
	 * 
	 * @param image
	 *            the {@link ImageView} to use
	 * @param dimension
	 *            the width of the image to set
	 */
	private void imageViewProperties(ImageView image, int dimension)
	{
		// set a fixed width for the given ImageView
		image.setFitWidth(dimension);
		// preserve the image ratio
		image.setPreserveRatio(true);
	}
	
	/**
	 * Convert a {@link Mat} object (OpenCV) in the corresponding {@link Image}
	 * for JavaFX
	 * 
	 * @param frame
	 *            the {@link Mat} representing the current frame
	 * @return the {@link Image} to show
	 */
	private Image mat2Image(Mat frame)
	{
		// create a temporary buffer
		MatOfByte buffer = new MatOfByte();
		// encode the frame in the buffer, according to the PNG format
		Imgcodecs.imencode(".png", frame, buffer);
		// build and return an Image created from the image encoded in the
		// buffer
		return new Image(new ByteArrayInputStream(buffer.toArray()));
	}
	
	/**
	 * Generic method for putting element running on a non-JavaFX thread on the
	 * JavaFX thread, to properly update the UI
	 * 
	 * @param property
	 *            a {@link ObjectProperty}
	 * @param value
	 *            the value to set for the given {@link ObjectProperty}
	 */
	private <T> void onFXThread(final ObjectProperty<T> property, final T value)
	{
		Platform.runLater(new Runnable() {
			
			@Override
			public void run()
			{
				property.set(value);
			}
		});
	}
	
}
