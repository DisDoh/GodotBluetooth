package ch.disd.godotbiling;

import android.util.Log;
import androidx.annotation.NonNull;
import androidx.collection.ArraySet;

import org.godotengine.godot.Dictionary;
import org.godotengine.godot.Godot;
import org.godotengine.godot.plugin.GodotPlugin;
import org.godotengine.godot.plugin.SignalInfo;
import org.godotengine.godot.plugin.UsedByGodot;
import ch.disd.godotbiling.utils.GooglePlayBillingUtils;
import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.AcknowledgePurchaseResponseListener;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.ConsumeParams;
import com.android.billingclient.api.ConsumeResponseListener;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.ProductDetailsResponseListener;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesResponseListener;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryPurchasesParams;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class GodotGooglePlayBilling extends GodotPlugin implements PurchasesUpdatedListener, BillingClientStateListener {

    private final BillingClient billingClient;
    private final HashMap<String, ProductDetails> productDetailsCache = new HashMap<>(); // productId → ProductDetails
    private boolean calledStartConnection;
    private String obfuscatedAccountId;
    private String obfuscatedProfileId;
    private static final String BILLING_LIBRARY_VERSION = "6.2.1"; // Actual version
    public static int logLevel = 0; // Added: default log level (is off), changed to public static for use in GooglePlayBillingUtils
    public static String logTag = "godot"; // Log TAG, changed to public static for use in GooglePlayBillingUtils

    public GodotGooglePlayBilling(Godot godot) {
        super(godot);

        billingClient = BillingClient
                .newBuilder(Objects.requireNonNull(getActivity()))
                .enablePendingPurchases()
                .setListener(this)
                .build();
        calledStartConnection = false;
        obfuscatedAccountId = "";
        obfuscatedProfileId = "";
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "GodotGooglePlayBilling";
    }

    @NonNull
    @Override
    public Set<SignalInfo> getPluginSignals() {
        Set<SignalInfo> signals = new ArraySet<>();

        signals.add(new SignalInfo("connected"));
        signals.add(new SignalInfo("disconnected"));
        signals.add(new SignalInfo("connect_error", Integer.class, String.class));
        signals.add(new SignalInfo("purchases_updated", Object[].class));
        signals.add(new SignalInfo("query_purchases_response", Object.class));
        signals.add(new SignalInfo("purchase_error", Integer.class, String.class));
        signals.add(new SignalInfo("product_details_query_completed", Object[].class));
        signals.add(new SignalInfo("product_details_query_error", Integer.class, String.class, String[].class));
        signals.add(new SignalInfo("purchase_acknowledged", String.class));
        signals.add(new SignalInfo("purchase_acknowledgement_error", Integer.class, String.class, String[].class));
        signals.add(new SignalInfo("purchase_consumed", String.class));
        signals.add(new SignalInfo("purchase_consumption_error", Integer.class, String.class, String.class));

        return signals;
    }

    private void log(String message) {
        if (logLevel > 0) {
            Log.i(logTag, message);
        }
    }

    @UsedByGodot
    public void setLogLevel(int level) {
        logLevel = level;
    }

    @UsedByGodot
    public void setLogTag(String tag) {
        logTag = tag; // Set tag from Godot
    }

    @Override
    public void onBillingSetupFinished(@NonNull BillingResult billingResult) {
        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
            log("Connected!");
            emitSignal("connected");
        } else {
            log("Connect Error!");
            emitSignal("connect_error", billingResult.getResponseCode(), billingResult.getDebugMessage());
        }
    }

    @Override
    public void onBillingServiceDisconnected() {
        log("Disconnected!");
        emitSignal("disconnected");
    }

    @UsedByGodot
    public void startConnection() {
        log("Start Connection!");
        calledStartConnection = true;
        billingClient.startConnection(this);
    }

    @UsedByGodot
    public void endConnection() {
        log("End Connection!");
        billingClient.endConnection();
    }

    @UsedByGodot
    public boolean isReady() {
        log("Is Ready: " + billingClient.isReady());
        return this.billingClient.isReady();
    }

    @UsedByGodot
    public int getConnectionState() {
        log("Connection State: " + billingClient.getConnectionState());
        return billingClient.getConnectionState();
    }

    @UsedByGodot
    public void queryPurchases(String type) {
        log("Query Purchases!");
        QueryPurchasesParams params = QueryPurchasesParams.newBuilder().setProductType(type).build();

        billingClient.queryPurchasesAsync(params, new PurchasesResponseListener() {
            @Override
            public void onQueryPurchasesResponse(@NonNull BillingResult billingResult,
                                                 @NonNull List<Purchase> purchaseList) {
                Dictionary returnValue = new Dictionary();
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    returnValue.put("status", 0); // OK = 0
                    returnValue.put("purchases", GooglePlayBillingUtils.convertPurchaseListToDictionaryObjectArray(purchaseList));
                } else {
                    returnValue.put("status", 1); // FAILED = 1
                    returnValue.put("response_code", billingResult.getResponseCode());
                    returnValue.put("debug_message", billingResult.getDebugMessage());
                }
                emitSignal("query_purchases_response", (Object) returnValue);
            }
        });
    }

    @UsedByGodot
    public void queryProductDetails(final String[] list, String type) {
        ArrayList<QueryProductDetailsParams.Product> products = new ArrayList<>();

        for (String productId : list) {
            products.add(QueryProductDetailsParams.Product.newBuilder().setProductId(productId).setProductType(type).build());
        }

        QueryProductDetailsParams params = QueryProductDetailsParams.newBuilder()
                .setProductList(products)
                .build();

        billingClient.queryProductDetailsAsync(params, new ProductDetailsResponseListener() {
            @Override
            public void onProductDetailsResponse(@NonNull BillingResult billingResult, @NonNull List<ProductDetails> productDetailsList) {
                log("Billing Result: " + billingResult);
                if (productDetailsList.isEmpty()) {
                    log("Product Details list is empty!");
                    }
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    for (ProductDetails productDetails : productDetailsList) {
                        productDetailsCache.put(productDetails.getProductId(), productDetails);
                    }
                    log("Product Details Query Completed for " + type + " products.");
                    emitSignal("product_details_query_completed", (Object) GooglePlayBillingUtils.convertProductDetailsListToDictionaryObjectArray(productDetailsList));
                } else {
                    log("Product Details Query Error for " + type + " products.");
                    emitSignal("product_details_query_error", billingResult.getResponseCode(), billingResult.getDebugMessage(), list);
                }

            }
        });
    }

    @UsedByGodot
    public void acknowledgePurchase(final String purchaseToken) {
        log("Acknowledging purchase: " + purchaseToken);

        AcknowledgePurchaseParams acknowledgePurchaseParams =
                AcknowledgePurchaseParams.newBuilder()
                        .setPurchaseToken(purchaseToken)
                        .build();
        billingClient.acknowledgePurchase(acknowledgePurchaseParams, new AcknowledgePurchaseResponseListener() {
            @Override
            public void onAcknowledgePurchaseResponse(@NonNull BillingResult billingResult) {
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    log("Purchase acknowledged successfully: " + purchaseToken);
                    emitSignal("purchase_acknowledged", purchaseToken);
                } else {
                    log("Failed to acknowledge purchase: " + billingResult.getDebugMessage());
                    emitSignal("purchase_acknowledgement_error", billingResult.getResponseCode(), billingResult.getDebugMessage(), purchaseToken);
                }
            }
        });
    }

    @UsedByGodot
    public void consumePurchase(String purchaseToken) {
        ConsumeParams consumeParams = ConsumeParams.newBuilder()
                .setPurchaseToken(purchaseToken)
                .build();

        billingClient.consumeAsync(consumeParams, new ConsumeResponseListener() {
            @Override
            public void onConsumeResponse(@NonNull BillingResult billingResult, @NonNull String purchaseToken) {
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    log("Purchase Consumed!");
                    emitSignal("purchase_consumed", purchaseToken);
                } else {
                    log("Purchase Consumption Error!");
                    emitSignal("purchase_consumption_error", billingResult.getResponseCode(), billingResult.getDebugMessage(), purchaseToken);
                }
            }
        });
    }

    @UsedByGodot
    public void purchase(String productId, String type, String accountId, String profileId) {
        this.obfuscatedAccountId = accountId;
        this.obfuscatedProfileId = profileId;

        log("Initiating purchase for product: " + productId); // Добавим логирование начала покупки
        log("Initiating purchase with type: " + type); // Добавим логирование типа покупки

        if (!productDetailsCache.containsKey(productId)) {
            log("Product details not available in cache for product: " + productId); // Сообщение о недоступности деталей продукта в кэше
            emitSignal("purchase_error", BillingClient.BillingResponseCode.SERVICE_DISCONNECTED, "Product details not available");
            return;
        }

        log("Product details found in cache: " + productDetailsCache.get(productId)); // Вывод информации о продукте из кэша

        log("Continuing purchase"); // Сообщение о том, что выполнение кода продолжается

        ProductDetails productDetails = productDetailsCache.get(productId);

        assert productDetails != null;
        BillingFlowParams.ProductDetailsParams.Builder productDetailsParamsBuilder = BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(productDetails);

        // Установка offerToken только для подписок
        if (type.equals("subs")) {
            assert productDetails.getSubscriptionOfferDetails() != null;
            String offerToken = productDetails.getSubscriptionOfferDetails().get(0).getOfferToken();
            productDetailsParamsBuilder.setOfferToken(offerToken);
        }

        BillingFlowParams.ProductDetailsParams productDetailsParams = productDetailsParamsBuilder.build();

        log("Continuing with productDetailsParams"); // Сообщение о том, что выполнение кода продолжается

        BillingFlowParams.Builder builder = BillingFlowParams.newBuilder();
        builder.setProductDetailsParamsList(Collections.singletonList(productDetailsParams));
        builder.setObfuscatedAccountId(this.obfuscatedAccountId);
        builder.setObfuscatedProfileId(this.obfuscatedProfileId);

        log("Continuing with Builder"); // Сообщение о том, что выполнение кода продолжается

        BillingResult billingResult = billingClient.launchBillingFlow(Objects.requireNonNull(getActivity()), builder.build());

        log("Continuing with billingResult"); // Сообщение о том, что выполнение кода продолжается

        if (billingResult.getResponseCode() != BillingClient.BillingResponseCode.OK) {
            log("Purchase Error!");
            emitSignal("purchase_error", billingResult.getResponseCode(), billingResult.getDebugMessage());
        }

        log("Ending method"); // Сообщение о том, что выполнение кода завершилось
    }

    @Override
    public void onPurchasesUpdated(@NonNull BillingResult billingResult, List<Purchase> purchases) {
        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && purchases != null) {
            log("Purchase Updated!");
            for (Purchase purchase : purchases) {
                log("Purchase details: " + purchase.getOriginalJson());
            }
            emitSignal("purchases_updated", (Object) GooglePlayBillingUtils.convertPurchaseListToDictionaryObjectArray(purchases));
        } else {
            log("Purchase Error: " + billingResult.getDebugMessage());
            emitSignal("purchase_error", billingResult.getResponseCode(), billingResult.getDebugMessage());
        }
    }
}
