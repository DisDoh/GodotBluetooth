package ch.disd.godotnfc;

import static android.nfc.NdefRecord.RTD_TEXT;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.nfc.tech.NdefFormatable;
import android.os.Build;
import android.os.Parcelable;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.collection.ArraySet;

import org.godotengine.godot.Godot;
import org.godotengine.godot.plugin.GodotPlugin;
import org.godotengine.godot.plugin.SignalInfo;
import org.godotengine.godot.plugin.UsedByGodot;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;

/**
 * Godot NFC Plugin
 * - Lecture de tags texte NDEF
 * - Écriture de texte sur le dernier tag détecté
 */
public class GodotNFC extends GodotPlugin {

    public static final String TAG = "GodotNFC";

    protected Activity activity = null;
    private final Godot godot;

    private NfcAdapter nfcAdapter = null;
    /**
     * -1 = pas de NFC
     *  0 = NFC désactivé
     *  1 = NFC activé
     */
    private int status = 0;

    // Dernier tag détecté (pour écriture)
    private Tag lastTag = null;

    /* Construction / base plugin
     * ********************************************************************** */

    public GodotNFC(Godot godot) {
        super(godot);
        this.godot = godot;
        this.activity = godot.getActivity();
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "GodotNFC";
    }

    @NonNull
    @Override
    public Set<SignalInfo> getPluginSignals() {
        Set<SignalInfo> signals = new ArraySet<>();

        // Status NFC (int)
        signals.add(new SignalInfo("nfc_enabled", Integer.class));
        // Lecture d’un tag texte
        signals.add(new SignalInfo("tag_readed", String.class));
        // Erreur générique
        signals.add(new SignalInfo("error"));
        // Résultat écriture (1 = OK, 0 = échec)
        signals.add(new SignalInfo("tag_written", Integer.class));

        return signals;
    }

    /* Utilitaire Toast
     * ********************************************************************** */

    private void showToast(final String msg) {
        if (activity == null) return;
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    /* Méthodes exposées à Godot
     * ********************************************************************** */

    /**
     * Init NFC (placeholder, cohérent avec le plugin Bluetooth)
     */
    @UsedByGodot
    public void init() {
        Log.d(TAG, "NFC init called");
        showToast("NFC init called");
    }

    /**
     * Active le foreground dispatch NFC et émet le signal "nfc_enabled"
     */
    @UsedByGodot
    public void enableNFC() {
        if (activity == null) {
            Log.e(TAG, "Activity is null in enableNFC");
            showToast("NFC: Activity is null in enableNFC");
            emitSignal("nfc_enabled", -1);
            return;
        }

        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                nfcAdapter = NfcAdapter.getDefaultAdapter(activity);

                if (nfcAdapter == null) {
                    status = -1; // Pas de NFC hardware
                    Log.e(TAG, "No NFC hardware");
                    showToast("NFC: no hardware");
                    emitSignal("nfc_enabled", status);
                    return;
                }

                if (nfcAdapter.isEnabled()) {
                    status = 1;

                    Intent intent = new Intent(activity, activity.getClass());
                    intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);

                    PendingIntent pendingIntent =
                            PendingIntent.getActivity(
                                    activity,
                                    0,
                                    intent,
                                    PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
                            );

                    nfcAdapter.enableForegroundDispatch(activity, pendingIntent, null, null);
                    Log.d(TAG, "NFC foreground dispatch enabled");
                    showToast("NFC enabled");
                } else {
                    status = 0; // NFC désactivé
                    Log.d(TAG, "NFC is disabled");
                    showToast("NFC disabled");
                }

                emitSignal("nfc_enabled", status);
            }
        });
    }

    /**
     * Désactive le foreground dispatch NFC.
     * Appel côté Godot : GodotNFC.disableNFC()
     */
    @UsedByGodot
    public void disableNFC() {
        if (activity == null) {
            Log.e(TAG, "Activity is null in disableNFC");
            showToast("NFC: Activity null in disableNFC");
            return;
        }

        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (nfcAdapter != null) {
                    try {
                        nfcAdapter.disableForegroundDispatch(activity);
                        Log.d(TAG, "NFC foreground dispatch disabled");
                        showToast("NFC disabled");
                    } catch (IllegalStateException e) {
                        Log.e(TAG, "Error disabling NFC foreground dispatch", e);
                        showToast("Error disabling NFC dispatch");
                    }
                }
            }
        });
    }

    /**
     * Retourne le status NFC:
     * -1 = pas de NFC, 0 = désactivé, 1 = activé
     */
    @UsedByGodot
    public int getStatus() {
        Log.d(TAG, "getStatus: " + status);
        showToast("NFC status: " + status);
        return status;
    }

    /**
     * Écrit un texte sur le dernier tag détecté.
     * Appel côté Godot : GodotNFC.writeText("Hello NFC")
     */
    @UsedByGodot
    public void writeText(final String text) {
        if (activity == null) {
            Log.e(TAG, "Activity is null in writeText");
            showToast("NFC: Activity null in writeText");
            emitSignal("tag_written", 0);
            emitSignal("error");
            return;
        }

        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (lastTag == null) {
                    Log.e(TAG, "No Tag available to write");
                    showToast("NFC: No tag available to write");
                    emitSignal("tag_written", 0);
                    emitSignal("error");
                    return;
                }

                try {
                    Ndef ndef = Ndef.get(lastTag);
                    NdefMessage message = new NdefMessage(
                            new NdefRecord[]{ createTextRecord(text) });

                    if (ndef != null) {
                        ndef.connect();

                        if (!ndef.isWritable()) {
                            Log.e(TAG, "Tag is not writable");
                            showToast("NFC: Tag not writable");
                            emitSignal("tag_written", 0);
                            emitSignal("error");
                            ndef.close();
                            return;
                        }

                        if (message.toByteArray().length > ndef.getMaxSize()) {
                            Log.e(TAG, "Message too large for tag");
                            showToast("NFC: Message too large");
                            emitSignal("tag_written", 0);
                            emitSignal("error");
                            ndef.close();
                            return;
                        }

                        ndef.writeNdefMessage(message);
                        ndef.close();

                        Log.i(TAG, "Tag written successfully");
                        showToast("NFC: Tag written successfully");
                        emitSignal("tag_written", 1);
                    } else {
                        // Tag non formaté NDEF, tentative de formatage
                        NdefFormatable formatable = NdefFormatable.get(lastTag);
                        if (formatable != null) {
                            formatable.connect();
                            formatable.format(message);
                            formatable.close();

                            Log.i(TAG, "Tag formatted and written successfully");
                            showToast("NFC: Tag formatted & written");
                            emitSignal("tag_written", 1);
                        } else {
                            Log.e(TAG, "Tag is not NDEF formatable");
                            showToast("NFC: Tag not NDEF formatable");
                            emitSignal("tag_written", 0);
                            emitSignal("error");
                        }
                    }

                } catch (Exception e) {
                    Log.e(TAG, "Error writing NFC tag", e);
                    showToast("NFC: Error writing tag");
                    emitSignal("tag_written", 0);
                    emitSignal("error");
                }
            }
        });
    }

    /* Cycle de vie / Intent
     * ********************************************************************** */

    /**
     * Appelé par Godot à la reprise de l'activité.
     * Utilisé pour traiter l’Intent NFC courant et émettre "tag_readed".
     */
    @Override
    public void onMainResume() {
        super.onMainResume();

        Intent intent = godot.getCurrentIntent();
        if (intent == null) {
            Log.e(TAG, "onMainResume: intent is null");
            showToast("NFC: onMainResume intent null");
            return;
        }

        Tag detectedTag = null;
        NdefMessage[] ndefMessages = null;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ : nouvelles API type-safe
            detectedTag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag.class);
            ndefMessages = intent.getParcelableArrayExtra(
                    NfcAdapter.EXTRA_NDEF_MESSAGES,
                    NdefMessage.class
            );
        } else {
            // Anciennes API (dépréciées mais 100% compatibles)
            Object rawTag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
            if (rawTag instanceof Tag) {
                detectedTag = (Tag) rawTag;
            }

            Parcelable[] rawMsgs = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
            if (rawMsgs != null) {
                ndefMessages = new NdefMessage[rawMsgs.length];
                for (int i = 0; i < rawMsgs.length; i++) {
                    ndefMessages[i] = (NdefMessage) rawMsgs[i];
                }
            }
        }

        Log.e(TAG, "Enter onMainResume " + intent.getType() + " " + intent.getAction());
        showToast("NFC: onMainResume action=" + intent.getAction());
        Log.e(TAG, "Tag " + detectedTag + " NFC Adapter extra tag " + NfcAdapter.EXTRA_TAG);

        if (detectedTag != null) {
            lastTag = detectedTag;
            showToast("NFC: Tag detected (resume)");
        }

        if (ndefMessages == null) {
            emitSignal("error");
            showToast("NFC: No NDEF messages (resume)");
            return;
        }

        for (NdefMessage ndefMessage : ndefMessages) {
            if (ndefMessage == null) continue;
            NdefRecord[] ndefRecords = ndefMessage.getRecords();
            if (ndefRecords == null) continue;

            for (NdefRecord ndefRecord : ndefRecords) {
                Log.e(TAG, "NFC Get Type " + ndefRecord.getType()[0]);
                String message = parseTextRecord(ndefRecord);
                showToast("NFC read: " + message);
                emitSignal("tag_readed", message);
            }
        }
    }

    /**
     * Nouveau : gère les nouveaux intents NFC quand l'activité est déjà ouverte.
     * Godot 3.x peut appeler cette méthode si configuré dans plugin.cfg.
     */
    public void onMainNewIntent(Intent intent) {
        if (intent == null) {
            Log.e(TAG, "onMainNewIntent: intent is null");
            showToast("NFC: onMainNewIntent intent null");
            return;
        }

        Tag detectedTag = null;
        NdefMessage[] ndefMessages = null;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            detectedTag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag.class);
            ndefMessages = intent.getParcelableArrayExtra(
                    NfcAdapter.EXTRA_NDEF_MESSAGES,
                    NdefMessage.class
            );
        } else {
            Object rawTag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
            if (rawTag instanceof Tag) {
                detectedTag = (Tag) rawTag;
            }

            Parcelable[] rawMsgs = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
            if (rawMsgs != null) {
                ndefMessages = new NdefMessage[rawMsgs.length];
                for (int i = 0; i < rawMsgs.length; i++) {
                    ndefMessages[i] = (NdefMessage) rawMsgs[i];
                }
            }
        }

        Log.e(TAG, "Enter onMainNewIntent " + intent.getType() + " " + intent.getAction());
        showToast("NFC: onMainNewIntent action=" + intent.getAction());
        Log.e(TAG, "Tag " + detectedTag + " NFC Adapter extra tag " + NfcAdapter.EXTRA_TAG);

        if (detectedTag != null) {
            lastTag = detectedTag;
            showToast("NFC: Tag detected (newIntent)");
        }

        if (ndefMessages == null) {
            emitSignal("error");
            showToast("NFC: No NDEF messages (newIntent)");
            return;
        }

        for (NdefMessage ndefMessage : ndefMessages) {
            if (ndefMessage == null) continue;
            NdefRecord[] ndefRecords = ndefMessage.getRecords();
            if (ndefRecords == null) continue;

            for (NdefRecord ndefRecord : ndefRecords) {
                Log.e(TAG, "NFC Get Type " + ndefRecord.getType()[0]);
                String message = parseTextRecord(ndefRecord);
                showToast("NFC read: " + message);
                emitSignal("tag_readed", message);
            }
        }
    }

    /* Utilitaires
     * ********************************************************************** */

    /**
     * Parse un NDEF Text Record (ou renvoie le payload brut si autre type).
     */
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

            return new String(
                    payload,
                    languageCodeLength + 1,
                    payload.length - languageCodeLength - 1,
                    textEncoding
            );
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * Construit un NDEF Text Record UTF-8 avec langue "en".
     */
    private NdefRecord createTextRecord(String text) {
        byte[] langBytes = "en".getBytes(StandardCharsets.US_ASCII);
        Charset utfEncoding = StandardCharsets.UTF_8;
        byte[] textBytes = text.getBytes(utfEncoding);

        int langLength = langBytes.length;
        int textLength = textBytes.length;

        byte[] payload = new byte[1 + langLength + textLength];
        // bit 7 = 0 pour UTF-8, bits 5..0 = longueur du code langue
        payload[0] = (byte) (langLength & 0x3F);
        System.arraycopy(langBytes, 0, payload, 1, langLength);
        System.arraycopy(textBytes, 0, payload, 1 + langLength, textLength);

        return new NdefRecord(
                NdefRecord.TNF_WELL_KNOWN,
                RTD_TEXT,
                new byte[0],
                payload
        );
    }
}
