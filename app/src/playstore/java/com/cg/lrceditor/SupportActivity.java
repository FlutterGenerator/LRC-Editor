package com.cg.lrceditor;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.QueryPurchaseHistoryParams;
import com.android.billingclient.api.QueryPurchasesParams;
import com.android.billingclient.api.SkuDetails;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class SupportActivity extends AppCompatActivity {
	private PurchaseItem[] purchaseItems;
	private BillingClient billingClient;

	private SharedPreferences preferences;
	private boolean isDarkTheme = false;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		preferences = getSharedPreferences("LRC Editor Preferences", MODE_PRIVATE);
		String theme = preferences.getString(Constants.THEME_PREFERENCE, "light");
		if (theme.equals("dark")) {
			isDarkTheme = true;
			setTheme(R.style.AppThemeDark);
		} else if (theme.equals("darker")) {
			isDarkTheme = true;
			setTheme(R.style.AppThemeDarker);
		}

		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_support);

		Toolbar toolbar = findViewById(R.id.toolbar);
		if (isDarkTheme) {
			toolbar.setPopupTheme(R.style.AppThemeDark_PopupOverlay);
		}
		setSupportActionBar(toolbar);

		try {
			getSupportActionBar().setDisplayHomeAsUpEnabled(true);
		} catch (NullPointerException e) {
			e.printStackTrace();
		}

		initPurchaseItems();
		initBilling();
	}

	private void initPurchaseItems() {
		purchaseItems = new PurchaseItem[]{
			new PurchaseItem("SKU1", findViewById(R.id.one)),
			new PurchaseItem("SKU2", findViewById(R.id.two)),
			new PurchaseItem("SKU3", findViewById(R.id.three)),
			new PurchaseItem("SKU4", findViewById(R.id.four)),
			new PurchaseItem("SKU5", findViewById(R.id.five)),
		};
	}

	/**
	 * Initializes billing modules
	 */
	private void initBilling() {
		billingClient = BillingClient.newBuilder(this)
			.setListener((billingResult, purchases) -> {
				if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK
					&& purchases != null) {
					for (Purchase purchase : purchases) {
						handlePurchase(purchase);
					}
				} else if (billingResult.getResponseCode() != BillingClient.BillingResponseCode.USER_CANCELED
					&& billingResult.getResponseCode() != BillingClient.BillingResponseCode.DEVELOPER_ERROR) {
					alert(getString(R.string.error), getErrorCodeString(getString(R.string.failed_to_complete_purchase_message), billingResult.getResponseCode()));
				}
			})
			.enablePendingPurchases()
			.build();

		initConnection();
	}

	private void handlePurchase(Purchase purchase) {
		if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
			if (!purchase.isAcknowledged()) {
				AcknowledgePurchaseParams acknowledgePurchaseParams =
					AcknowledgePurchaseParams.newBuilder()
						.setPurchaseToken(purchase.getPurchaseToken())
						.build();
				billingClient.acknowledgePurchase(acknowledgePurchaseParams, billingResult -> {
					if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
						grantPurchasePerks();
						// Обновлено для v5+
						for (String productId : purchase.getProducts()) {
							int skuIndex = getSkuIndex(productId);
							if (skuIndex != -1) purchaseItems[skuIndex].setPurchased();
						}
						queryPurchaseHistoryAsync(); // Update purchases
					} else {
						alert(getString(R.string.error), getErrorCodeString(getString(R.string.failed_to_acknowledge_purchase), billingResult.getResponseCode()));
					}
				});
			}
		} else {
			alert(getString(R.string.error), getErrorCodeString(getString(R.string.failed_to_validate_purchase), purchase.getPurchaseState()));
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		queryPurchases();
	}

	/**
	 * Connects billing with Google Play servers
	 */
	private void initConnection() {
		if (billingClient.isReady()) return;

		billingClient.startConnection(new BillingClientStateListener() {
			@Override
			public void onBillingSetupFinished(BillingResult billingResult) {
				if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
					queryInventory();
				}
			}

			@Override
			public void onBillingServiceDisconnected() {
				alert(getString(R.string.error), getString(R.string.failed_to_connect_to_gplay));
				updatePurchaseButtonTexts();
			}
		});
	}

	/**
	 * Queries for available products
	 */
	private void queryInventory() {
		if (!billingClient.isReady()) {
			initConnection();
			return;
		}

		List<String> skuStringList = getSkuStringList();
		List<QueryProductDetailsParams.Product> products = new ArrayList<>();
		for (String sku : skuStringList) {
			products.add(QueryProductDetailsParams.Product.newBuilder()
				.setProductId(sku)
				.setProductType(BillingClient.ProductType.INAPP)
				.build());
		}

		QueryProductDetailsParams params = QueryProductDetailsParams.newBuilder()
			.setProductList(products)
			.build();

		billingClient.queryProductDetailsAsync(params, (billingResult, productDetailsList) -> {
			if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
				if (productDetailsList != null) {
					String[] skuStrings = skuStringList.toArray(new String[0]);
					Collections.sort(productDetailsList, (pd1, pd2) -> {
						for (String skuString : skuStrings) {
							if (pd1.getProductId().equals(skuString)) return -1;
							else if (pd2.getProductId().equals(skuString)) return 1;
						}
						return 0;
					});

					for (int i = 0; i < productDetailsList.size(); i++) {
						purchaseItems[i].setSku(productDetailsList.get(i));
					}
				}
				queryPurchaseHistoryAsync();
			} else {
				alert(getString(R.string.error), getErrorCodeString(getString(R.string.failed_to_load_products), billingResult.getResponseCode()));
				updatePurchaseButtonTexts();
			}
		});
	}

	private List<String> getSkuStringList() {
		List<String> skuStringList = new ArrayList<>();
		for (PurchaseItem purchaseItem : purchaseItems) {
			skuStringList.add(purchaseItem.getSkuString());
		}
		return skuStringList;
	}

	/**
	 * Queries and updates recent purchase history including cancelled ones
	 */
	private void queryPurchaseHistoryAsync() {
		if (!billingClient.isReady()) {
			initConnection();
			return;
		}

		billingClient.queryPurchaseHistoryAsync(
			QueryPurchaseHistoryParams.newBuilder()
				.setProductType(BillingClient.ProductType.INAPP)
				.build(),
			(billingResult, list) -> {
				if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
					if (list != null) queryPurchases();
				} else {
					alert(getString(R.string.error), getErrorCodeString(getString(R.string.failed_to_check_purchase_history), billingResult.getResponseCode()));
					updatePurchaseButtonTexts();
				}
			});
	}

	/**
	 * Queries purchases made by using the local Google Play Store cache data
	 */
	private void queryPurchases() {
		if (!billingClient.isReady()) {
			initConnection();
			return;
		}

		billingClient.queryPurchasesAsync(
			QueryPurchasesParams.newBuilder()
				.setProductType(BillingClient.ProductType.INAPP)
				.build(),
			(billingResult, purchasesList) -> {
				if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
					if (purchasesList != null) {
						boolean invalidSku = false;
						for (Purchase purchase : purchasesList) {
							for (String productId : purchase.getProducts()) {
								int skuIndex = getSkuIndex(productId);
								if (skuIndex == -1) {
									invalidSku = true;
									break;
								}
								if (purchase.getPurchaseState() != Purchase.PurchaseState.PENDING) {
									purchaseItems[skuIndex].setPurchased();
									grantPurchasePerks();
								}
							}
						}

						if (invalidSku) {
							alert(getString(R.string.error), getString(R.string.could_not_validate_skus));
						} else if (purchasesList.size() == 0) {
							revokePurchasePerks();
						}
					}
				} else {
					alert(getString(R.string.error), getErrorCodeString(getString(R.string.failed_to_check_local_purchase), billingResult.getResponseCode()));
				}
				updatePurchaseButtonTexts();
			});
	}

	public void makePurchase(View view) {
		int index = Integer.parseInt(view.getTag().toString());
		SkuDetails skuDetails = purchaseItems[index - 1].getSku();
		if (skuDetails != null) {
			BillingFlowParams billingFlowParams = BillingFlowParams.newBuilder()
				.setProductDetailsParamsList(
					Arrays.asList(
						BillingFlowParams.ProductDetailsParams.newBuilder()
							.build()
					)
				)
				.build();
			int responseCode = billingClient.launchBillingFlow(this, billingFlowParams).getResponseCode();
			if (responseCode != BillingClient.BillingResponseCode.OK) {
				alert(getString(R.string.error), getErrorCodeString(getString(R.string.failed_to_open_purchase_dialog), responseCode));
			}
		} else {
			alert(getString(R.string.please_wait), getString(R.string.iaps_loading_message));
		}
	}

	private void grantPurchasePerks() {
		if (!preferences.getString(Constants.PURCHASED_PREFERENCE, "").equals("Y")) {
			alert(getString(R.string.purchase_successful), getString(R.string.thank_you_for_the_purchase_message));
			SharedPreferences.Editor editor = preferences.edit();
			editor.putString(Constants.PURCHASED_PREFERENCE, "Y");
			editor.apply();
		}
	}

	private void revokePurchasePerks() {
		if (preferences.getString(Constants.PURCHASED_PREFERENCE, "").equals("Y")) {
			SharedPreferences.Editor editor = preferences.edit();
			editor.putString(Constants.PURCHASED_PREFERENCE, "");
			editor.putString(Constants.THEME_PREFERENCE, "light");
			editor.apply();
		}
	}

	private int getSkuIndex(String skuString) {
		for (int i = 0; i < purchaseItems.length; i++) {
			if (purchaseItems[i].getSkuString().equals(skuString)) {
				return i;
			}
		}
		return -1;
	}

	private void updatePurchaseButtonTexts() {
		for (PurchaseItem purchaseItem : purchaseItems) {
			purchaseItem.updateButtonText(this);
		}
	}

	private void alert(String title, String message) {
		try {
			new AlertDialog.Builder(SupportActivity.this)
				.setTitle(title)
				.setMessage(message)
				.setPositiveButton(getString(R.string.ok), null)
				.create()
				.show();
		} catch (WindowManager.BadTokenException e) {
			Log.w("LRC Editor - Support", "[" + title + "] " + message);
		}
	}

	private String getErrorCodeString(String error, int errorCode) {
		return error + ".\n" + getString(R.string.error_code, errorCode);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getItemId() == android.R.id.home) {
			onBackPressed();
			return true;
		}
		return super.onOptionsItemSelected(item);
	}
}
