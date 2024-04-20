
package ch.disd.godotnfc;

import static android.nfc.NdefRecord.RTD_TEXT;
import org.godotengine.godot.Godot;
import org.godotengine.godot.plugin.GodotPlugin;
import org.godotengine.godot.plugin.SignalInfo;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

import android.app.Activity;

import android.content.Intent;
import android.app.PendingIntent;

import android.nfc.NfcAdapter;

import android.nfc.NdefRecord;
import android.nfc.NdefMessage;
import android.nfc.Tag;
import android.os.Parcelable;
import android.util.Log;

public class GodotNFC extends GodotPlugin {

    private Activity activity = null; // The main activity of the game

    public static final String TAG = "GodotNFC";

    private Godot godot = null;

    private NfcAdapter nfcAdapter = null;
    private int status = 0;


    public GodotNFC(Godot godot) {
        super(godot);
        this.godot = godot;
        activity = getActivity();
    }

    @Override
    public String getPluginName() {
        return "GodotNFC";
    }

    @Override
    public List<String> getPluginMethods() {
        return Arrays.asList(
                "init",
                "enableNFC",
                "getStatus"
        );
    }

    @Override
    public Set<SignalInfo> getPluginSignals() {
        Set<SignalInfo> signals = new HashSet<>();

        signals.add(new SignalInfo("nfc_enabled", Integer.class));
        signals.add(new SignalInfo("tag_readed", String.class));
        signals.add(new SignalInfo("error"));

        return signals;
    }

    public void init(final int script_id) {

    }


    public void enableNFC() {
        activity.runOnUiThread(new Runnable() {
            public void run()
            {
                nfcAdapter = NfcAdapter.getDefaultAdapter(activity);

                if (nfcAdapter == null) {
                    status = -1;
                }

                if (nfcAdapter.isEnabled()) {
                    status = 1;

                    Intent intent = new Intent(activity, activity.getClass());
                    intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);

                    PendingIntent pendingIntent = PendingIntent.getActivity(activity,0, intent,0);
                    //IntentFilter[] intentFilter = new IntentFilter[]{};

                    nfcAdapter.enableForegroundDispatch(activity, pendingIntent,null,null);
                }
                else {
                    status = 0;
                }

                emitSignal("nfc_enabled", status);
            }
        });
    }

    public int getStatus()
    {
        return status;
    }
//    @Override
//    public void onNewIntent(android.content.Intent intent){
//        if (intent.getAction().equals(NfcAdapter.ACTION_TAG_DISCOVERED)) {
//            Log.e(TAG, "NFC Tag\n" + ByteArrayToHexString(intent.getByteArrayExtra(NfcAdapter.EXTRA_ID)));
//        }
//        Log.e(TAG, "Entered OnNewIntend ");
//    }

    void processIntent(Intent intent) {
        Parcelable[] rawMsgs = intent.getParcelableArrayExtra(
                NfcAdapter.EXTRA_NDEF_MESSAGES);
        // only one message sent during the beam
        NdefMessage msg = (NdefMessage) rawMsgs[0];
        // record 0 contains the MIME type, record 1 is the AAR, if present
   }
    public static String parseTextRecord(NdefRecord ndefRecord) {

        if (ndefRecord.getTnf() != NdefRecord.TNF_WELL_KNOWN) {
            return new String(ndefRecord.getPayload());
        }
        if (!Arrays.equals(ndefRecord.getType(), NdefRecord.RTD_TEXT)) {
            return new String(ndefRecord.getPayload());
        }
        try {

            byte[] payload = ndefRecord.getPayload();

            String textEncoding = ((payload[0] & 0x80) == 0) ? "UTF-8" : "UTF-16";

            int languageCodeLength = payload[0] & 0x3f;

            String languageCode = new String(payload, 1, languageCodeLength, "US-ASCII");

            String textRecord = new String(payload, languageCodeLength + 1,
                    payload.length - languageCodeLength - 1, textEncoding);
            return textRecord;
        } catch (Exception e) {
            throw new IllegalArgumentException();
        }
    }
    public void onMainResume() {
        //For some reason, this gets also called (and onMainPause), when... an Intent happens...? ...why? Lets use this I guess, since it doesn't look like there is another way to get an Intent...
        Intent intent = godot.getCurrentIntent();

        Tag detectedTag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
        Log.e(TAG,"Enter onMainResume " + intent.getType() + " " + intent.getAction());
        Log.e(TAG,"Tag " + detectedTag + " NFC Adapter extra tag " + NfcAdapter.EXTRA_TAG);
        if (intent.hasExtra(NfcAdapter.EXTRA_TAG)) {
            Parcelable[] parcelables = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
            if (parcelables == null) {

                emitSignal("error");
                return;
            }
            for (int i = 0; i < parcelables.length; ++i) {
                NdefMessage ndefMessage = (NdefMessage)parcelables[i];
                NdefRecord[] ndefRecords = ndefMessage.getRecords();
                if (ndefRecords == null)
                    continue;

                for (int j = 0; j < ndefRecords.length; ++j) {
                    NdefRecord ndefRecord = ndefRecords[j];
                    byte[] payload = ndefRecord.getPayload();

                    Log.e(TAG,"NFC Get Type " + ndefRecord.getType()[0]);
                    String message = parseTextRecord(ndefRecord);

                    emitSignal("tag_readed", message);
                  //  emitSignal("tag_readed", String.valueOf(m.get(1)));
                }
            }
        }

    }

}
