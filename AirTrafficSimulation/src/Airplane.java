import java.util.Random;

//I just want to see that this shows up on git
//I added another comment

public class Airplane extends Thread{
	private double x; //must be in [0, 100]
	private double xDot;
	private double y; //must be in [0, 100]
	private double yDot; //overall speed (function of xDot and yDot) must
	//be in [5, 10]
	private double theta; //must be in [-pi, pi]
	private double thetaDot; //must be in [-pi/4, pi/4]
	private Simulator s;

	private final int MINSPEED = 5;
	private final int MAXSPEED = 10;
	private final int MINCOORDINATE = 0;
	private final int MAXCOORDINATE = 100;
	private final double MINOMEGA = -Math.PI/4;
	private final double MAXOMEGA = Math.PI/4;
	
	//no noise
	private final double DOWNRANGE_VAR = Math.sqrt(0);
	private final double CROSSRANGE_VAR = Math.sqrt(0);
	//a bit of noise
//	private final double DOWNRANGE_VAR = Math.sqrt(.01);
//	private final double CROSSRANGE_VAR = Math.sqrt(.01);

	private double fuelLevel;
	
	private boolean flying;
	
	private String name;
	
	public Airplane(double[] pose, double s, double omega, Simulator sim, double startFuel){
		this.s = sim;

		if (pose.length != 3){
			throw new IllegalArgumentException("The pose must have length 3");
		}		
		this.x = this.withinBounds(pose[0], MINCOORDINATE, MAXCOORDINATE);
		this.y = this.withinBounds(pose[1], MINCOORDINATE, MAXCOORDINATE);
		this.theta = this.convertTheta(pose[2]);
		this.thetaDot = this.withinBounds(omega, MINOMEGA, MAXOMEGA);

		double speed = this.withinBounds(s, MINSPEED, MAXSPEED);
		this.xDot = speed*Math.cos(this.theta);
		this.yDot = speed*Math.sin(this.theta);

		this.fuelLevel = startFuel;
		this.flying = false;
	}

	public void run(){
		boolean done = false;
		double time = 0;
		double oldTime = 0;
		while (!done){
			synchronized(this.s){
				try{
					time = this.s.getCurrentSec() + .001*this.s.getCurrentMSec();
					if (time >= oldTime + .01){
						double timeDif = time-oldTime;
						int sec = (int)(timeDif);
						int msec = (int) ((timeDif*1000)%1000);
						this.advance(1000*sec + msec);
						oldTime = time;
					}
					else{
						s.wait();
					}
					if (time >= this.s.simDuration/1000){
						done = true;
					}
					
				}
				catch (InterruptedException ie){
					System.err.println("There was an interrupted exception");
					System.err.println(ie);
				}
				s.notifyAll();
			}
		}
	}


	/**
	 * @return an array of 3 doubles that say the x, y, and theta
	 * of the ground vehicle
	 */
	public synchronized double[] getPosition(){
		double[] returnable = new double[3];
		returnable[0] = this.x;
		returnable[1] = this.y;
		returnable[2] = this.theta;

		return returnable;
	}

	/**
	 * @return an array of 3 doubles that say the x velocity, y
	 * velocity, and angular velocity of the ground vehicle
	 */
	public synchronized double[] getVelocity(){
		double[] returnable = new double[3];
		returnable[0] = this.xDot;
		returnable[1] = this.yDot;
		returnable[2] = this.thetaDot;

		return returnable;
	}
	
	public synchronized double getFuelLevel(){
		return this.fuelLevel;
	}
	
	public synchronized void setFuelLevel(double newFuel){
		this.fuelLevel = newFuel;
	}
	
	public synchronized boolean getFlying(){
		return this.flying;
	}
	
	public synchronized void setFlying(boolean shouldItFly){
		this.flying = shouldItFly;
	}

	/**
	 * Given an x, y, and theta desired, the ground vehicle updates its current
	 * position to reflect the new parameters (within legal bounds)
	 * @param desiredPosition
	 */
	public synchronized void setPosition(double[] desiredPosition){
		if (desiredPosition.length != 3){
			throw new IllegalArgumentException("The input array must have"
					+ " exactly 3 entries!");
		}

		double desiredX = desiredPosition[0];
		double desiredY = desiredPosition[1];
		double desiredTheta = desiredPosition[2];

		this.x = withinBounds(desiredX, MINCOORDINATE, MAXCOORDINATE);
		this.y = withinBounds(desiredY, MINCOORDINATE, MAXCOORDINATE);
		this.theta = convertTheta(desiredTheta);
	}


	/**
	 * If the linear velocity is too great, preserve the angle but scale down the
	 * x and y components to create a legal linear velocity in the same direction.
	 * If the angular velocity is out of bounds, use the boundary closest to the desired
	 * omega.
	 * @param desiredVelocity
	 */
	public synchronized void setVelocity(double[] desiredVelocity){
		if (desiredVelocity.length != 3){
			throw new IllegalArgumentException("The input array must have"
					+ " exactly 3 entries!");
		}

		double desiredXDot = desiredVelocity[0];
		double desiredYDot = desiredVelocity[1];
		double desiredOmega = desiredVelocity[2];

		double velocityAngle = Math.atan(desiredYDot/desiredXDot);
		boolean flippedAngle = desiredXDot < 0; //take care of case when velocities point into
		//quadrants 2 or 3

		double desiredSpeed = Math.sqrt(Math.pow(desiredXDot, 2) + Math.pow(desiredYDot, 2));
		double newSpeed = withinBounds(desiredSpeed, MINSPEED, MAXSPEED);
		if (flippedAngle){
			newSpeed = -1*newSpeed;
		}
		double newXDot = newSpeed*Math.cos(velocityAngle);
		double newYDot = newSpeed*Math.sin(velocityAngle);

		double newOmega = withinBounds(desiredOmega, MINOMEGA, MAXOMEGA);

		this.xDot = newXDot;
		this.yDot = newYDot;
		this.thetaDot = newOmega;
	}

	/**
	 * Derives target velocities from the Control parameter and then
	 * uses setVelocity() to update current velocities to the new
	 * targets.
	 * If c is null, don't change anything
	 * @param c
	 */
	public synchronized void controlVehicle(Control c){
		if (c == null){
			return;
		}
		double desiredSpeed = this.withinBounds(c.getSpeed(), MINSPEED, MAXSPEED);
		double desiredOmega = this.withinBounds(c.getRotVel(), MINOMEGA, MAXOMEGA);

		double desiredXDot = desiredSpeed*Math.cos(this.theta);
		double desiredYDot = desiredSpeed*Math.sin(this.theta);

		double[] desiredVelocities = {desiredXDot, desiredYDot, desiredOmega};
		this.setVelocity(desiredVelocities);
	}

	/**
	 * Describes what the ground vehicle would do if it did not receive
	 * new instructions for sec+.001*msec seconds. Depending on original
	 * positions, velocity, angle, and angular velocity, this method
	 * updates the state variables to represent the new positions and
	 * velocities of the vehicle after a period of time.
	 * @param sec
	 * @param msec
	 */
	public synchronized void advance(int msec){
		Random r = new Random();
		double errD = r.nextGaussian()*DOWNRANGE_VAR;
		double errC = r.nextGaussian()*CROSSRANGE_VAR;
		
		
		double time = .001*msec;

		if (time < 0){
			throw new RuntimeException("You should not pass in a negative value for time");
		}
		//System.out.println(this.fuelLevel);
		
		if (Math.abs(this.fuelLevel) < msec && this.fuelLevel <= 0 ){ //the first term is just to prevent
			//constant spamming of calling setFlying(false);
			//this way it only calls that a few times when the plane barely has negative fuel
			this.setFlying(false);
			return;
		}
		
		if (!flying){
			return; //don't change anything if flying (the plane shouldn't move)
			//we also assume that staying at an airport doesn't burn any fuel.
		}
		
		double linSpeed = this.withinBounds(Math.sqrt(Math.pow(this.xDot, 2) + Math.pow(this.yDot, 2)), MINSPEED, MAXSPEED);

		double angSpeed = this.withinBounds(this.thetaDot, MINOMEGA, MAXOMEGA);
		
		this.fuelLevel -= time; //burn 1 unit of fuel per 1 second
		
		if (angSpeed == 0){ //if going straight, just update x and y
			this.x = this.withinBounds(this.x + linSpeed*time*Math.cos(this.theta), 0, 100) + Math.cos(this.theta)*errD - Math.sin(this.theta)*errC;
			this.y = this.withinBounds(this.y + linSpeed*time*Math.sin(this.theta), 0, 100) + Math.sin(this.theta)*errD + Math.sin(this.theta)*errC;
			return;
		}
		//if not going straight, use geometry to compute an arc
		double radius = linSpeed/angSpeed;
		double angleChange = time*angSpeed;
		double gamma = Math.PI - this.theta - angleChange/2;

		this.x = this.withinBounds(this.x - 2*radius*Math.sin(angleChange/2)*Math.cos(gamma), MINCOORDINATE, MAXCOORDINATE) + Math.cos(this.theta)*errD - Math.sin(this.theta)*errC;
		this.y = this.withinBounds(this.y + 2*radius*Math.sin(angleChange/2)*Math.sin(gamma), MINCOORDINATE, MAXCOORDINATE) + Math.sin(this.theta)*errD + Math.sin(this.theta)*errC;

		this.theta = this.convertTheta(this.theta + angleChange);

		this.xDot = linSpeed*Math.cos(this.theta);
		this.yDot = linSpeed*Math.sin(this.theta);
	}

	/**
	 * Returns the closest double to desired that is within lowBound and upBound. If
	 * desired is between the two bounds, just return desired. Otherwise, return the
	 * appropriate bound.
	 * @param desired: target double
	 * @param lowBound: lower bound on legal numbers
	 * @param upBound: upper bound on legal numbers
	 * @return: double closest to desired that is within lowBound and upBound
	 */
	private synchronized double withinBounds(double desired, double lowBound, double upBound){
		if (desired < lowBound){
			return lowBound;
		}
		else if (desired > upBound){
			return upBound;
		}
		return desired;
	}

	/**
	 * @param theta: any angle in radians (can be outside bounds of -pi to pi)
	 * @return an equivalent angle in radians between -pi and pi
	 */
	private synchronized double convertTheta(double theta){
		while (theta < 0){
			theta = theta + 2*Math.PI; //easier to work with theta if positive
		}

		if (theta >= -1*Math.PI && theta < Math.PI){
			return theta;
		}
		double modded = theta%(2*Math.PI);
		if (modded < Math.PI){
			return modded;
		}
		return modded - 2*Math.PI;
	}


	public void setPlaneName(String name){
		this.name = name;
	}
	
	public String getPlaneName(){
		return this.name;
	}
	
	@Override
	public synchronized String toString(){
		String returnable = "Flight " + this.name;
		return returnable;
	}
}