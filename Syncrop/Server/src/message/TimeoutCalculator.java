package message;

public class TimeoutCalculator {

	private int timeout;
	private int expectedRoundTripTimeMax;
	private float srtt=1000;
	private float rttdev=0;
	private float alpha=.25f;
	private float beta=.125f;
	
	private final long MAX_PING_DELAY=32000,MIN_PING_DELAY=1000;
	private long pingDelay=MIN_PING_DELAY;
	public TimeoutCalculator(){}
	public TimeoutCalculator(float alpha,float beta){
		this.alpha=alpha;
		this.beta=beta;
	}
	public  void calcualteTimeout(int rtt){
		srtt=rtt*alpha+srtt*(1-alpha);
		rttdev=Math.abs(rtt-srtt)*beta+rttdev*(1-beta);
		int newExpectedRoundTripTimeMax=(int)(srtt+4*rttdev);
		
		if(Math.abs(newExpectedRoundTripTimeMax-expectedRoundTripTimeMax)<.1*expectedRoundTripTimeMax)
			pingDelay=Math.min(pingDelay*2, MAX_PING_DELAY);
		else pingDelay=Math.max(newExpectedRoundTripTimeMax, MIN_PING_DELAY);
		
		timeout=expectedRoundTripTimeMax*2;
	}
	public int getExpectedMaxRoundTripTime(){return expectedRoundTripTimeMax;}
	public int getTimeout(){return timeout;}
	public long getPingDelay(){return pingDelay;}
	
}
