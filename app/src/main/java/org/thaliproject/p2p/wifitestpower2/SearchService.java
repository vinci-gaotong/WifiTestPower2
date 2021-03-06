package org.thaliproject.p2p.wifitestpower2;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.BatteryManager;
import android.os.Binder;
import android.os.CountDownTimer;
import android.os.IBinder;
import android.util.Log;

import java.util.Collection;
import java.util.List;

/**
 * Created by juksilve on 6.3.2015.
 */
public class SearchService extends Service implements WifiBase.WifiStatusCallBack {

    SearchService that = this;

    static final public String DSS_WIFIDIRECT_VALUES = "test.microsoft.com.wifidirecttest.DSS_WIFIDIRECT_VALUES";
    static final public String DSS_WIFIDIRECT_MESSAGE = "test.microsoft.com.wifidirecttest.DSS_WIFIDIRECT_MESSAGE";

    Boolean ITurnedWifiOff = false;

    CountDownTimer ServiceFoundTimeOutTimer = new CountDownTimer(600000, 1000) {
        public void onTick(long millisUntilFinished) {
            // not using
        }
        public void onFinish() {
            ResetCounterCount = ResetCounterCount + 1;
            //Restart service discovery
            //startServices();

            //switch off Wlan :)
            if(mWifiBase != null) {
                ITurnedWifiOff = true;
                mWifiBase.setWifiEnabled(false);
            }
        }
    };

    // 20 minute timer
    CountDownTimer SaveDataTimeOutTimer = new CountDownTimer(1200000, 1000) {
        public void onTick(long millisUntilFinished) {
            // not using
        }
        public void onFinish() {
            if (mTestDataFile != null) {
                //long Started , long got , long Noservices ,long Peererr ,long ServiceErr , long AddreqErr ,long  resetcounter) {
                mTestDataFile.WriteDebugline(lastChargePercent,StartedServicesCount,fullRoundCount,noServicesCount,PeerErrorCount,ServErrorCount,AddRErrorCount,ResetCounterCount,PeerChangedEventCount,PeerDiscoveryStoppedCount,PeerStartCount,LocalSErrorCount);
            }
            SaveDataTimeOutTimer.start();
        }
    };

    long LocalSErrorCount = 0;
    long PeerStartCount = 0;
    long PeerErrorCount = 0;
    long ServErrorCount = 0;
    long AddRErrorCount = 0;
    long ResetCounterCount = 0;
    long StartedServicesCount = 0;
    long fullRoundCount = 0;
    long noServicesCount = 0;
    long PeerChangedEventCount = 0;
    long PeerDiscoveryStoppedCount = 0;

    String latsDbgString = "";
    WifiBase mWifiBase = null;
    WifiServiceAdvertiser mWifiAccessPoint = null;
    WifiServiceSearcher mWifiServiceSearcher = null;

    IntentFilter mfilter = null;
    BroadcastReceiver mReceiver = null;
    TestDataFile mTestDataFile = null;
    int lastChargePercent = -1;

    private final IBinder mBinder = new MyLocalBinder();

    @Override
    public void WifiStateChanged(int state) {
        if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
            // we got wifi back, so we can re-start now
            print_line("WB", "Wifi is now enabled !");
            startServices();
        } else {
            //no wifi availavble, thus we need to stop doing anything;
            print_line("WB", "Wifi is DISABLEd !!");
            stopServices();

            if(mWifiBase != null && ITurnedWifiOff) {
                ITurnedWifiOff = false;
                mWifiBase.setWifiEnabled(true);
            }
        }
    }

    @Override
    public void StartedServiceDiscovery() {
        StartedServicesCount = StartedServicesCount + 1;
    }


    @Override
    public void gotServicesList(List<ServiceItem> list) {
        if(mWifiServiceSearcher != null & mWifiBase != null) {
            // Select service, save it in a list and start connection with it
            // and do remember to cancel Searching
            if (list != null && list.size() > 0) {
                print_line("SS", "Services found: " + list.size());

                fullRoundCount = fullRoundCount + 1;

                ServiceItem selItem = mWifiBase.SelectServiceToConnect(list);
                if (selItem != null) {
                    String message = "Round " + fullRoundCount + ", we found " + list.size()+ " servces and selected " + selItem.deviceName;

                    Intent intent = new Intent(DSS_WIFIDIRECT_VALUES);
                    intent.putExtra(DSS_WIFIDIRECT_MESSAGE, message);
                    sendBroadcast(intent);

                    ServiceFoundTimeOutTimer.cancel();
                    ServiceFoundTimeOutTimer.start();

                    print_line("", "Sent broadcast : " + message);
                } else {
                    // we'll get discovery stopped event soon enough
                    // and it starts the discovery again, so no worries :)
                    print_line("", "No devices selected");
                }
            }else{
                noServicesCount = noServicesCount + 1;
                print_line("", "No services found ?? from: " + list.size() + " service");
            }
        }
    }

    @Override
    public void PeerstartNow() {PeerStartCount = PeerStartCount + 1;}

    @Override
    public void LocalServiceStartError(int error) {LocalSErrorCount = LocalSErrorCount + 1;}

    @Override
    public void PeerStartError(int error) {
        PeerErrorCount = PeerErrorCount + 1;
    }

    @Override
    public void ServiceStartError(int error) {
        ServErrorCount = ServErrorCount + 1;
    }

    @Override
    public void AddReqStartError(int error) {
        AddRErrorCount = AddRErrorCount + 1;
    }

    @Override
    public void PeerChangedEvent() {PeerChangedEventCount = PeerChangedEventCount + 1;

    }

    @Override
    public void PeerDiscoveryStopped() {PeerDiscoveryStoppedCount = PeerDiscoveryStoppedCount + 1;

    }

    public class MyLocalBinder extends Binder {
        SearchService getService() {
            return SearchService.this;
        }
    }
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    public int onStartCommand(Intent intent, int flags, int startId) {
        print_line("SearchService","onStartCommand rounds so far :" + fullRoundCount);
        Start();
        return START_STICKY;
    }


    @Override
    public void onDestroy() {
        print_line("SearchService","onDestroy");
        super.onDestroy();
        ServiceFoundTimeOutTimer.cancel();
        Stop();
    }

    public void Start() {
        Stop();

        mTestDataFile = new TestDataFile(this);
        SaveDataTimeOutTimer.start();

        mfilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        mReceiver = new PowerConnectionReceiver();
        registerReceiver(mReceiver, mfilter);

        if(mWifiBase == null){
            mWifiBase = new WifiBase(this,this);
            mWifiBase.Start();
        }

        startServices();
    }

    public void Stop() {

        if(mReceiver != null){
            unregisterReceiver(mReceiver);
            mReceiver = null;
        }

        stopServices();
        if(mWifiBase != null){
            mWifiBase.Stop();
            mWifiBase = null;
        }

        if(mTestDataFile != null) {
            print_line("SearchService","Closing File");
            mTestDataFile.CloseFile();
            mTestDataFile = null;
        }

        SaveDataTimeOutTimer.cancel();
    }

    long roundsCount(){
        return fullRoundCount;
    }

    String getLastDbgString() {

        String ret = "Started : " + StartedServicesCount + ", got: " + fullRoundCount + "\n";
        ret =  ret + "Reset counter: " + ResetCounterCount + ", last charge: " + lastChargePercent + "%\n";
        ret =  ret + "PErr: " + PeerErrorCount + ", SErr: " + ServErrorCount + ", AddErr: " + AddRErrorCount + ", No service counter: " + noServicesCount + ", local Service errors: "+ LocalSErrorCount + "\n";
        ret =  ret + "last: " + latsDbgString + "\n";

        return ret;
    }

    boolean isRunnuing(){
        boolean ret = false;
        if(mWifiBase != null){
            ret = true;
        }
        return ret;
    }

    private void stopServices(){
        print_line("","Stoppingservices");
        if(mWifiAccessPoint != null){
            mWifiAccessPoint.Stop();
            mWifiAccessPoint = null;
        }

        if(mWifiServiceSearcher != null){
            mWifiServiceSearcher.Stop();
            mWifiServiceSearcher = null;
        }
    }

    private void startServices(){

        stopServices();

        WifiP2pManager.Channel channel = null;
        WifiP2pManager p2p = null;

        if(mWifiBase != null){
            channel = mWifiBase.GetWifiChannel();
            p2p = mWifiBase.GetWifiP2pManager();
        }

        if(channel != null && p2p != null) {


            // lets just kick the new channel a bit, and ask for peers
            p2p.discoverPeers(channel, new WifiP2pManager.ActionListener() {
                public void onSuccess() {}
                public void onFailure(int reason) {}
            });


            print_line("", "Starting services");
            mWifiAccessPoint = new WifiServiceAdvertiser(p2p, channel,that);
            mWifiAccessPoint.Start("powerTests");

            mWifiServiceSearcher = new WifiServiceSearcher(this, p2p, channel, that);
            mWifiServiceSearcher.Start();

            // start timeout timer to re-start discovery if it stops
            ServiceFoundTimeOutTimer.start();
        }
    }

    public void print_line(String who, String line) {
        latsDbgString = who + " : " + line;
        Log.i("BtTestMaa" + who, line);
    }

    public class PowerConnectionReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
            int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            lastChargePercent = (level*100)/scale;
            //String message = "Battery charge: " + lastChargePercent + " %";
        }
    }
}



