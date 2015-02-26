package com.dylan.cannonblockbreaker;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Bundle;
import android.util.AttributeSet;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

public class CannonView extends SurfaceView implements SurfaceHolder.Callback {

	private static final String TAG = "CannonView"; // for logging errors
	
	private CannonThread cannonThread; // controls the game loop
	private Activity activity; // to display Game Over dialog in GUI thread
	private boolean dialogIsDisplayed = false;
	
	//constants for game play
	public static final int TARGET_PIECES = 7; // sections in the target
	public static final int MISS_PENALTY =2; // seconds deducted on a miss
	public static final int HIT_REWARD = 3; // seconds added on a hit
	
	//variables for the game loop and tracking statistics
	private boolean gameOver; // is the game over?
	private double timeLeft; // time remaining in seconds
	private int shotsFired; // shots the user has fired
	private double totalElapsedTime; // elapsed seconds
	
	//variables for the blocker and target
	private Line blocker; // start and end points of the blocker
	private int blockerDistance; // blocker distance from left
	private int blockerBeginning; // blocker top-edge distance from top
	private int blockerEnd; // blocker bottom-edge distance from top
	private int initialBlockerVelocity; // initial blocker speed multiplier
	private float blockerVelocity; // blocker speed multiplier during game
	
	private Line target; // start and end points of the target
	private int targetDistance; // target distance from left
	private int targetBeginning; // target distance from top
	private double pieceLength; // length of a target piece
	private int targetEnd; // target bottom's distance from top
	private int initialTargetVelocity; // initieal target speed multiplier
	private float targetVelocity; // target speed multiplier
	
	private int lineWidth; // width of the target and blocker
	private boolean[] hitStates; // is each target piece hit?
	private int targetPiecesHit; // number of target pieces hit (out of 7)
	
	// variables for the cannon and cannonball
	private Point cannonball; // cannonball image's upper-left corner
	private int cannonballVelocityX; //cannonball's x velocity
	private int cannonballVelocityY; //cannonball's y velocity
	private boolean cannonballOnScreen; // whether cannonball on the screen
	private int cannonballRadius; // cannonball's radius
	private int cannonballSpeed; // cannonball's speed
	private int cannonBaseRadius; // cannon base's radius
	private int cannonLength; // cannon barrel's length
	private Point barrelEnd; // the endpoint of the cannon's barrel
	private int screenWidth;
	private int screenHeight;
	
	// constants and variables for managing sounds
	private static final int TARGET_SOUND_ID = 0;
	private static final int CANNON_SOUND_ID = 1;
	private static final int BLOCKER_SOUND_ID = 2;
	private SoundPool soundPool; // plays sound effects
	private SparseIntArray soundMap; // maps IDs to SoundPool
	
	// Paint variables used when drawing each item on the screen
	private Paint textPaint; // Paint used to draw text
	private Paint cannonballPaint; // Paint used to draw the cannonball
	private Paint cannonPaint; // Paint used to draw the cannon
	private Paint blockerPaint; // Paint used to draw the blocker
	private Paint targetPaint; // Paint used to draw the target
	private Paint backgroundPaint; // Paint used to clear the drawing area
	
	// **CONSTRUCTORS**
	public CannonView(Context context) {
		super(context);
		// TODO Auto-generated constructor stub
	}
	
	public CannonView(Context context, AttributeSet attrs) {
		super(context, attrs); //call superclass constructor
		activity = (Activity) context; // store reference to MainActivity
		
		// register SurfaceHolder.Callback listener
		getHolder().addCallback(this);
		
		// initialize Lines and Point representing game items
		blocker = new Line(); // create the blocker as a Line
		target = new Line(); // create the target as a Line
		cannonball = new Point(); // create the cannonball as a Point
		
		// initialize hitStates as a boolean array
		hitStates = new boolean[TARGET_PIECES];
		
		// initialize SoundPool to play the app's three sound effects
		soundPool = new SoundPool(1, AudioManager.STREAM_MUSIC, 0);
		
		// create Map of sounds and pre-load sounds
		soundMap = new SparseIntArray(3); // create new SparseIntArray
		soundMap.put(TARGET_SOUND_ID, soundPool.load(context, R.raw.target_hit, 1));
		soundMap.put(CANNON_SOUND_ID, soundPool.load(context,  R.raw.cannon_fire, 1));
		soundMap.put(BLOCKER_SOUND_ID, soundPool.load(context, R.raw.blocker_hit, 1));
		
		// construct Paints for drawing text, cannonball, cannon, 
		// blocker and target;  these are configured in method onSizeChanged
		textPaint = new Paint();
		cannonPaint = new Paint();
		cannonballPaint = new Paint();
		blockerPaint = new Paint();
		targetPaint = new Paint();
		backgroundPaint = new Paint();
		
	}// end CannonView constructor
	
	// called by surfaceChanged when the size of the SurfaceView changes,
	// such as when it's first added to the View hierarchy
	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh) {
	    super.onSizeChanged(w, h, oldw, oldh);

	    screenWidth = w; // store CannonView's width
	    screenHeight = h; // store CannonView's height
	    cannonBaseRadius = h / 18; // cannon base radius 1/18 screen height
	    cannonLength = w / 8; // cannon length 1/8 screen width

	    cannonballRadius = w / 36; // cannonball radius 1/36 screen width
	    cannonballSpeed = w * 3 / 2; // cannonball speed multiplier

	    lineWidth = w / 24; // target and blocker 1/24 screen width

	    // configure instance variables related to the blocker
	    blockerDistance = w * 5 / 8; // blocker 5/8 screen width from left
	    blockerBeginning = h / 8; // distance from top 1/8 screen height
	    blockerEnd = h * 3 / 8; // distance from top 3/8 screen height
	    initialBlockerVelocity = h / 2; // initial blocker speed multiplier
	    blocker.start = new Point(blockerDistance, blockerBeginning);
	    blocker.end = new Point(blockerDistance, blockerEnd);

	    // configure instance variables related to the target
	    targetDistance = w * 7 / 8; // target 7/8 screen width from left
	    targetBeginning = h / 8; // distance from top 1/8 screen height
	    targetEnd = h * 7 / 8; // distance from top 7/8 screen height
	    pieceLength = (targetEnd - targetBeginning) / TARGET_PIECES;
	    initialTargetVelocity = -h / 4; // initial target speed multiplier
	    target.start = new Point(targetDistance, targetBeginning);
	    target.end = new Point(targetDistance, targetEnd);

	    // endpoint of the cannon's barrel initially points horizontally
	    barrelEnd = new Point(cannonLength, h / 2);

		// configure Paint objects for drawing game elements
		textPaint.setTextSize(w / 20); // text size 1/20 of screen width
	    textPaint.setAntiAlias(true); // smoothes the text
		cannonPaint.setStrokeWidth(lineWidth * 1.5f); // set line thickness
		blockerPaint.setStrokeWidth(lineWidth); // set line thickness      
	    targetPaint.setStrokeWidth(lineWidth); // set line thickness       
	    backgroundPaint.setColor(Color.WHITE); // set background color

	    newGame(); // set up and start a new game
		} // end method onSizeChanged
}
