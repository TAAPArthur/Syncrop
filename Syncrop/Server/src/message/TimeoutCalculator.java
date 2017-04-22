package message;

public class TimeoutCalculator {

	private volatile int timeout=100000;
	private volatile int expectedRoundTripTimeMax;
	private float srtt=1000;
	private float rttdev=0;
	private float alpha=.25f;
	private float beta=.125f;
	
	public static final long MAX_PING_DELAY=32000,MIN_PING_DELAY=1000;
	private volatile long lastUpdate=System.currentTimeMillis();
	private volatile long pingDelay=MIN_PING_DELAY;
	public TimeoutCalculator(){}
	public TimeoutCalculator(float alpha,float beta){
		this.alpha=alpha;
		this.beta=beta;
	}
	public  void calculateTimeout(int rtt){
		srtt=rtt*alpha+srtt*(1-alpha);
		rttdev=Math.abs(rtt-srtt)*beta+rttdev*(1-beta);
		int newExpectedRoundTripTimeMax=(int)(srtt+4*rttdev);
		
		if(Math.abs(newExpectedRoundTripTimeMax-expectedRoundTripTimeMax)<.1*expectedRoundTripTimeMax)
			pingDelay=Math.min(pingDelay*2, MAX_PING_DELAY);
		else pingDelay=Math.max(newExpectedRoundTripTimeMax, MIN_PING_DELAY);
		expectedRoundTripTimeMax=newExpectedRoundTripTimeMax;
		timeout=(int) Math.max(expectedRoundTripTimeMax,pingDelay)*2;
		lastUpdate=System.currentTimeMillis();
	}
	public long getTimeOfLastUpdate(){return lastUpdate;}
	public int getExpectedMaxRoundTripTime(){return expectedRoundTripTimeMax;}
	public int getTimeout(){return timeout;}
	public long getPingDelay(){return pingDelay;}
	
}
