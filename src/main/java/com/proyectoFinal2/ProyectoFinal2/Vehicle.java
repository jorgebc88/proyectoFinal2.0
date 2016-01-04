package com.proyectoFinal2.ProyectoFinal2;

import org.opencv.core.Point;

public class Vehicle {
	private Point massCenterLocation = new Point(0, 0);
	private double vehicleSize = 0;
	private boolean counted = false;
	private boolean isGoingUp = false;
	private boolean isGoingDown = false;

	
	public boolean isGoingDown() {
		return isGoingDown;
	}

	public void setGoingDown(boolean isGoingDown) {
		this.isGoingDown = isGoingDown;
	}

	public boolean isGoingUp() {
		return isGoingUp;
	}

	public void setGoingUp(boolean isGoingUp) {
		this.isGoingUp = isGoingUp;
	}

	public Vehicle() {
		super();
	}
	
	public Vehicle(Point massCenterLocation, double vehicleSize, boolean counted, boolean isGoingDown) {
		super();
		this.massCenterLocation = massCenterLocation;
		this.vehicleSize = vehicleSize;
		this.counted = counted;
		if(isGoingDown == true){
			this.isGoingUp = false;
			this.isGoingDown = isGoingDown;
		}else{
			this.isGoingUp = true;
			this.isGoingDown = isGoingDown;
		}
	}
	
	public Point getMassCenterLocation() {
		return massCenterLocation;
	}
	
	public void setMassCenterLocation(Point massCenterLocation) {
		this.massCenterLocation = massCenterLocation;
	}
	
	public double getVehicleSize() {
		return vehicleSize;
	}
	
	public void setVehicleSize(double vehicleSize) {
		this.vehicleSize = vehicleSize;
	}
	
	public boolean isCounted() {
		return counted;
	}
	
	public void setCounted(boolean counted) {
		this.counted = counted;
	}
	
	@Override
	public String toString(){
		return "Centro de masa: " + this.massCenterLocation + " Tama�o: " + this.vehicleSize + " IsGoingUp: " + this.isGoingUp ;
	}
	
}
