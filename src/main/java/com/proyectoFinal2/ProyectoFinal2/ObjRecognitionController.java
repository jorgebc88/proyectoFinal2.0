package com.proyectoFinal2.ProyectoFinal2;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.imgproc.Moments;
import org.opencv.video.BackgroundSubtractor;
import org.opencv.video.BackgroundSubtractorKNN;
import org.opencv.video.Video;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.Videoio;

import dataBaseConnection.ObjectRandom;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.control.ProgressBar;

/**
 * The controller associated with the only view of our application. The
 * application logic is implemented here. It handles the button for
 * starting/stopping the camera, the acquired video stream, the relative
 * controls and the image segmentation process.
 *
 * @author <a href="mailto:luigi.derussis@polito.it">Luigi De Russis</a>
 * @version 1.5 (2015-11-26)
 * @since 1.0 (2015-01-13)
 */
public class ObjRecognitionController {

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

    BackgroundSubtractorKNN backgroundSubtractor = Video.createBackgroundSubtractorKNN();
    // BackgroundSubtractor backgroundSubtractor1 = new BackgroundSubtractor();

    private Mat prevFrame = null;

    private int autos = 0, motos = 0, omnibus = 0;

    private Set<Vehicle> vehicleSet = new HashSet<>();

    private double yLinePosition = 0;

    // property for object binding
    private ObjectProperty<String> hsvValuesProp;
    private double currentValue = 0;

    /**
     * The action triggered by pushing the button on the GUI
     */
    @FXML
    private void startCamera() {
        // bind a text property with the string containing the current range of
        // HSV values for object detection
        hsvValuesProp = new SimpleObjectProperty<>();
        this.hsvCurrentValues.textProperty().bind(hsvValuesProp);

        // set a fixed width for all the image to show and preserve image ratio
        this.imageViewProperties(this.originalFrame, 800);
        this.imageViewProperties(this.maskImage, 400);
        this.imageViewProperties(this.morphImage, 400);


        if (!this.cameraActive) {
            // start the video capture
            this.capture.open("resources/video/55 grados JE.avi");
//            this.capture.open("F:/Proyecto Final Facultad/Video Mate de luna 3/JP/Avi/Xbox/MVI_3109_mpeg4.avi");
            this.currentTime.setMax(
                    (this.capture.get(Videoio.CAP_PROP_FRAME_COUNT) / this.capture.get(Videoio.CAP_PROP_FPS)) * 1000);
            // is the video stream available?
            if (this.capture.isOpened()) {
                this.cameraActive = true;

                // grab a frame every 33 ms (30 frames/sec)
                this.capture.set(0, this.currentValue);
                Runnable frameGrabber = new Runnable() {

                    @Override
                    public void run() {

                        Image imageToShow = grabFrame();
                        originalFrame.setImage(imageToShow);
                    }
                };

                this.timer = Executors.newSingleThreadScheduledExecutor();
                this.timer.scheduleAtFixedRate(frameGrabber, 0, 33, TimeUnit.MILLISECONDS);// 33

                // update the button content
                this.cameraButton.setText("Stop Camera");
            } else {
                // log the error
                System.err.println("Failed to open the camera connection...");
            }
        } else {
            // the camera is not active at this point
            this.cameraActive = false;
            // update again the button content
            this.cameraButton.setText("Start Camera");
            // stop the timer
            try {
                this.timer.shutdown();
                this.timer.awaitTermination(33, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                // log the exception
                System.err.println("Exception in stopping the frame capture, trying to release the camera now... " + e);
            }
            // autos = 0;
            // vehicleSet.clear();
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
    private Image grabFrame() {

        this.currentTime.setValue(this.capture.get(Videoio.CAP_PROP_POS_MSEC));
        this.progressBar.setProgress(
                (this.capture.get(Videoio.CAP_PROP_POS_FRAMES) / this.capture.get(Videoio.CAP_PROP_FRAME_COUNT)));
        this.capture.set(Videoio.CAP_PROP_POS_MSEC, this.currentTime.getValue());

        // init everything
        Image imageToShow = null;
        Mat frame = new Mat();

        // check if the capture is open
        if (this.capture.isOpened()) {
            try {
                // read the current frame
                this.capture.read(frame);
                this.yLinePosition = frame.height() * 0.6;
                // if the frame is not empty, process it
                if (!frame.empty()) {
                    // init
                    Mat blurredImage = new Mat();
                    Mat grayImage = new Mat();

                    // remove some noise
                    Imgproc.blur(frame, blurredImage, new Size(7, 7));

                    // convert the frame to GRAY
                    Imgproc.cvtColor(blurredImage, grayImage, Imgproc.COLOR_BGR2GRAY);
                    if (this.prevFrame == null) {
                        this.prevFrame = grayImage;
                    }
                    // get thresholding values from the UI
                    // remember: H ranges 0-180, S and V range 0-255

                    // show the current selected HSV range
                    String valuesToPrint = "AUTOS: " + this.autos
                            + "\tMOTOS: " + this.motos + "\tCOLECTIVOS: "
                            + this.omnibus;
                    this.onFXThread(this.hsvValuesProp, valuesToPrint);

                    Mat fgmask = new Mat();
                    fgmask.create(frame.size(), frame.type());

                    backgroundSubtractor.apply(frame, fgmask);

                    Imgproc.GaussianBlur(fgmask, fgmask, new Size(11, 11), 3.5, 3.5);
                    this.onFXThread(this.maskImage.imageProperty(), this.mat2Image(fgmask));

                    Imgproc.threshold(fgmask, fgmask, 128, 255, Imgproc.THRESH_BINARY);
                    this.onFXThread(this.morphImage.imageProperty(), this.mat2Image(fgmask));

                    backgroundSubtractor.getBackgroundImage(grayImage);
                    // find the vehicles contours and show them
                    this.prevFrame = grayImage;
                    // find the vehicles contours and show them
                    frame = this.findAndDetectVehicles(fgmask, frame);

                    // convert the Mat object (OpenCV) to Image (JavaFX)
                    imageToShow = mat2Image(frame);
                }
            } catch (Exception e) {
                // log the (full) error
                System.err.print("ERROR");
                e.printStackTrace();
            }
        }

        return imageToShow;
    }

    /**
     * Given a binary image containing one or more closed surfaces, use it as a
     * mask to find and highlight the objects contours
     *
     * @param maskedImage the binary image to be used as a mask
     * @param frame       the original {@link Mat} image to be used for drawing the
     *                    objects contours
     * @return the {@link Mat} image with the objects contours framed
     */
    private Mat findAndDetectVehicles(Mat maskedImage, Mat frame) {
        // init
        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();

        // find contours
        Imgproc.findContours(maskedImage, contours, hierarchy, Imgproc.RETR_CCOMP, Imgproc.CHAIN_APPROX_SIMPLE);
        Mat contour;
        Rect rect;
        String type;
        // if any contour exist...
        Imgproc.line(frame, new Point(0, this.yLinePosition), new Point( (frame.width()), this.yLinePosition),
                new Scalar(0, 0, 250));
        if (hierarchy.size().height > 0 && hierarchy.size().width > 0) {
            // for each contour, display it in blue
            for (int idx = 0; idx >= 0; idx = (int) hierarchy.get(0, idx)[0]) {
                contour = contours.get(idx);
                rect = Imgproc.boundingRect(contours.get(idx));
                if (rect.area() > 2000) {
                    if (rect.area() < 15000) {
                        type = "Bike";
                    } else if (rect.area() < 40000) {
                        type = "Car";
                    } else {
                        type = "Bus";
                    }
                    try {
                        this.defineVehicle(rect, frame.width(), type);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    Imgproc.drawMarker(frame, this.calculateMassCenterRectangle(rect), new Scalar(0, 250, 0));
                    Imgproc.rectangle(frame, new Point(rect.x, rect.y),
                            new Point(rect.x + rect.width, rect.y + rect.height), new Scalar(0, 250, 0));
                }
            }
        }
        return frame;
    }

    private Point calculateMassCenterRectangle(Rect rect) {
        double x, y;
        x = rect.x + (rect.width / 2);
        y = rect.y + (rect.height / 2);
        return new Point(x, y);

    }

    private Point calculateMassCenter(Mat contour) {
        Moments moment = Imgproc.moments(contour);
        double cx = moment.m10 / moment.m00;
        double cy = moment.m01 / moment.m00;
        return new Point(cx, cy);
    }

    private void defineVehicle(Rect rect, int frameWidth, String type) throws Exception {
        Vehicle vehicleAux;
        boolean isANewVehicle = true;
        Date date = new Date();

        if (vehicleSet.isEmpty()) {
            if (this.calculateMassCenterRectangle(rect).y < this.yLinePosition) {
                date.setTime(System.currentTimeMillis());
                vehicleAux = new Vehicle(this.calculateMassCenterRectangle(rect), rect.area(), false,
                        this.isGoingDown(rect, frameWidth), date);
                vehicleSet.add(vehicleAux);
                return;
            }
        }
        for (Vehicle vehicle : vehicleSet) {
            if (this.isMoving(vehicle.getMassCenterLocation(), this.calculateMassCenterRectangle(rect))) {
                isANewVehicle = false;
                vehicle.setMassCenterLocation(this.calculateMassCenterRectangle(rect));
                vehicle.setVehicleSize(rect.area());
                date.setTime(System.currentTimeMillis());
                vehicle.setDetectionDate(date);
                if (!vehicle.isGoingUp()) {
                    if (!vehicle.isCounted() && this.shouldBeCounted(rect)) {
                        vehicle.setCounted(true);
                        if (type.equals("Bike")) {
                            this.motos++;
                            System.out.println(type + ": " + this.motos + " " + vehicle);
                        } else if(type.equals("Car")){
                            this.autos++; // modificar!!
                            System.out.println(type + ": " + this.autos + " " + vehicle);
                        }else {
                            this.omnibus++; // modificar!!
                            System.out.println(type + ": " + this.omnibus + " " + vehicle);

                        }
                        System.out.println("Total: " + (this.autos + this.motos));
                        // ObjectRandom.httpPost(type,
                        // vehicle.getDetectionDate());
                        this.vehicleSet.remove(vehicle);
                    }
                }
                break;
            }
        }
        if (isANewVehicle) {
            if (this.calculateMassCenterRectangle(rect).y < this.yLinePosition) {
                date.setTime(System.currentTimeMillis());
                vehicleAux = new Vehicle(this.calculateMassCenterRectangle(rect), rect.area(), false,
                        this.isGoingDown(rect, frameWidth), date);
                vehicleSet.add(vehicleAux);
            }
        }
    }

    private boolean isGoingDown(Rect rect, int frameWidth) {
        if (this.calculateMassCenterRectangle(rect).x < frameWidth) {
            return true;
        }
        return false;
    }

    private boolean shouldBeCounted(Rect rect) {
        double newYPosition = (this.calculateMassCenterRectangle(rect)).y;
        if (newYPosition > this.yLinePosition) {
            return true;
        }
        return false;
    }

    private boolean isMoving(Point oldCalculateMassCenter, Point newMassCenterLocation) {
        if (newMassCenterLocation.x >= oldCalculateMassCenter.x * 0.80
                && newMassCenterLocation.x <= oldCalculateMassCenter.x * 1.20) {
            if (newMassCenterLocation.y >= oldCalculateMassCenter.y * 0.8
                    && newMassCenterLocation.y <= oldCalculateMassCenter.y * 1.2) {
                return true;
            }
        }
        return false;
    }

    private boolean areTheSameSize(double oldVehicle, double newVehicle) {
        if (newVehicle >= oldVehicle * 0.80 && newVehicle <= oldVehicle * 1.20) {
            return true;
        }
        return false;

    }

    /**
     * Set typical {@link ImageView} properties: a fixed width and the
     * information to preserve the original image ration
     *
     * @param image     the {@link ImageView} to use
     * @param dimension the width of the image to set
     */
    private void imageViewProperties(ImageView image, int dimension) {
        // set a fixed width for the given ImageView
        image.setFitWidth(dimension);
        // preserve the image ratio
        image.setPreserveRatio(true);
    }

    /**
     * Convert a {@link Mat} object (OpenCV) in the corresponding {@link Image}
     * for JavaFX
     *
     * @param frame the {@link Mat} representing the current frame
     * @return the {@link Image} to show
     */
    private Image mat2Image(Mat frame) {
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
     * @param property a {@link ObjectProperty}
     * @param value    the value to set for the given {@link ObjectProperty}
     */
    private <T> void onFXThread(final ObjectProperty<T> property, final T value) {
        Platform.runLater(new Runnable() {

            @Override
            public void run() {
                property.set(value);
            }
        });
    }

}
