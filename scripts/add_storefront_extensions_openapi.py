"""Document the storefront extension routes (guest checkout, returns, cart, online payments, GHTK, social sign-in,
email verification, stock alerts, size charts) in docs/openapi.json. Safe to run again: it overwrites only these routes.

Run from any directory: python scripts/add_storefront_extensions_openapi.py
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
spec_path = ROOT / "docs/openapi.json"
spec = json.loads(spec_path.read_text(encoding="utf-8"))
paths, schemas = spec["paths"], spec["components"]["schemas"]
API = {"$ref": "#/components/schemas/ApiResponse"}


def ref(name):
    return {"$ref": f"#/components/schemas/{name}"}


def answer(description, schema=None):
    return {"description": description, "content": {"application/json": {"schema": schema or API}}}


ERRORS = {
    "400": "Missing or invalid values.",
    "401": "Authentication required.",
    "403": "Missing role or permission.",
    "404": "Resource not found.",
    "409": "Conflicting state, stock, payment or refund limit.",
    "429": "Too many requests from this client; see Retry-After.",
}


def op(method, path, tag, operation_id, summary, description, *, public=False, body=None, params=None, ok="200",
       errors=("400", "404"), consumes="application/json", response=None):
    operation = {"tags": [tag], "operationId": operation_id, "summary": summary, "description": description}
    operation["security"] = [] if public else [{"bearerAuth": []}]
    if params:
        operation["parameters"] = params
    if body:
        if isinstance(body, dict):
            operation["requestBody"] = {"required": True, "content": body}
        else:
            operation["requestBody"] = {"required": True, "content": {consumes: {"schema": ref(body)}}}
    responses = {ok: answer({"200": "Success", "201": "Created", "202": "Accepted", "204": "No content"}[ok], response)}
    if ok == "204":
        responses[ok] = {"description": "No content"}
    for code in errors:
        responses[code] = answer(ERRORS[code])
    if not public:
        responses.setdefault("401", answer(ERRORS["401"]))
        responses.setdefault("403", answer(ERRORS["403"]))
    operation["responses"] = dict(sorted(responses.items()))
    paths.setdefault(path, {})[method] = operation


def query(name, schema, required=False):
    return {"name": name, "in": "query", "required": required, "schema": schema}


def path_param(name):
    return {"name": name, "in": "path", "required": True, "schema": {"type": "string", "format": "uuid"}}


def obj(properties, required=()):
    schema = {"type": "object", "properties": properties}
    if required:
        schema["required"] = list(required)
    return schema


S = {"type": "string"}
UUID = {"type": "string", "format": "uuid"}
INT = {"type": "integer"}
MONEY = {"type": "number"}
area = obj({"province": S, "district": S, "ward": S, "street": S}, ["province"])
item = obj({"productVariantId": UUID, "quantity": {"type": "integer", "minimum": 1, "maximum": 1000}}, ["productVariantId", "quantity"])
schemas.update({
    "ShippingArea": area,
    "CheckoutItem": item,
    "EmailVerificationRequest": obj({"token": S}, ["token"]),
    "GoogleLoginRequest": obj({"credential": {"type": "string", "description": "ID token from the Google Identity Services button"}}, ["credential"]),
    "FacebookLoginRequest": obj({"accessToken": {"type": "string", "description": "User access token from the Facebook JavaScript SDK"}}, ["accessToken"]),
    "SocialProviders": obj({"googleClientId": S, "facebookAppId": S}),
    "GuestPayRequest": obj({"orderCode": S, "phone": S, "method": {"type": "string", "enum": ["VNPAY", "MOMO"]}}, ["orderCode", "phone", "method"]),
    "PayRequest": obj({"method": {"type": "string", "enum": ["VNPAY", "MOMO"]}}, ["method"]),
    "PaymentRedirect": obj({"gateway": S, "payUrl": S, "reference": S, "amount": MONEY}),
    "PaymentResult": obj({"orderId": UUID, "orderCode": S, "gateway": S, "status": {"type": "string", "enum": ["PAID", "FAILED", "PENDING"]}, "amount": MONEY}),
    "GatewayStatus": obj({"gateway": S, "configured": {"type": "boolean"}, "ipnUrl": S}),
    "ShippingQuoteRequest": obj({"shippingMethodCode": S, "area": ref("ShippingArea"), "items": {"type": "array", "items": ref("CheckoutItem")}},
                                ["shippingMethodCode", "items"]),
    "ShippingQuote": obj({"shippingMethodCode": S, "shippingFee": MONEY, "freeShipping": {"type": "boolean"},
                          "live": {"type": "boolean", "description": "True when the fee came from the carrier for this address"}}),
    "StockAlertRequest": obj({"productVariantId": UUID, "email": {"type": "string", "format": "email"}}, ["productVariantId", "email"]),
    "StockAlertSummary": obj({"productVariantId": UUID, "productId": UUID, "productName": S, "sku": S, "colorName": S, "sizeName": S,
                              "waiting": INT, "available": INT, "latestAt": {"type": "string", "format": "date-time"}}),
    "CartRequest": obj({"items": {"type": "array", "maxItems": 100, "items": ref("CheckoutItem")}}, ["items"]),
    "CartLine": obj({"variantId": UUID, "productId": UUID, "slug": S, "name": S, "imageUrl": S, "colorName": S, "sizeName": S, "sku": S,
                     "price": MONEY, "quantity": INT, "available": INT, "sellable": {"type": "boolean"}}),
    "Suggestions": obj({"products": {"type": "array", "items": {"type": "object"}}, "categories": {"type": "array", "items": {"type": "object"}},
                        "brands": {"type": "array", "items": {"type": "object"}}}),
    "ExchangeOption": obj({"variantId": UUID, "colorName": S, "sizeName": S, "available": INT}),
    "CustomerReturnItem": obj({"orderItemId": UUID, "quantity": INT, "reason": S, "replacementVariantId": UUID}, ["orderItemId", "quantity"]),
    "CustomerReturnRequest": obj({"orderId": UUID, "requestType": {"type": "string", "enum": ["RETURN", "EXCHANGE"]}, "reason": S,
                                  "items": {"type": "array", "items": ref("CustomerReturnItem")},
                                  "images": {"type": "array", "maxItems": 6, "items": S}},
                                 ["orderId", "requestType", "reason", "items", "images"]),
    "SizeChart": obj({"columns": {"type": "array", "minItems": 2, "maxItems": 8, "items": S},
                      "rows": {"type": "array", "minItems": 1, "maxItems": 30, "items": {"type": "array", "items": S}}, "note": S},
                     ["columns", "rows"]),
    "CarrierShipmentRequest": obj({"orderId": UUID}, ["orderId"]),
    "CarrierStatus": obj({"carrier": S, "configured": {"type": "boolean"}}),
})
for field, schema in {"email": {"type": "string", "format": "email", "description": "Required for guest checkout; the confirmation goes here"},
                      "area": ref("ShippingArea")}.items():
    schemas["CheckoutRequest"].setdefault("properties", {})[field] = schema

ME = "Requires CUSTOMER role and an active linked customer profile; the customer comes from the JWT. See docs/storefront-extensions.md."

# Auth
op("post", "/api/v1/auth/email/verify", "Auth", "verifyEmail", "Confirm an email address",
   "Public. Spends the one-time link emailed at registration (valid 24 hours). Rate limited per client.",
   public=True, body="EmailVerificationRequest", errors=("400", "429"))
op("post", "/api/v1/auth/email/resend", "Auth", "resendVerification", "Send the verification link again",
   "Requires AUTH_PROFILE_READ. At most 3 links per hour; earlier links stop working. 409 EMAIL_ALREADY_VERIFIED.",
   ok="202", errors=("409", "429"))
op("get", "/api/v1/auth/providers", "Auth", "signInProviders", "Social sign-in buttons to show",
   "Public. Google client id and Facebook app id when configured; never secrets.", public=True, errors=(), response=None)
op("post", "/api/v1/auth/google", "Auth", "googleLogin", "Sign in with Google",
   "Public. Verifies the Google ID token (signature, issuer, audience = GOOGLE_CLIENT_ID). A known identity signs in; otherwise the verified email "
   "links an existing customer account or creates one. Staff accounts are refused (401). Rate limited per client.",
   public=True, body="GoogleLoginRequest", errors=("400", "401", "429"))
op("post", "/api/v1/auth/facebook", "Auth", "facebookLogin", "Sign in with Facebook",
   "Public. Checks the access token with Graph API debug_token for this app, then reads id, name and email. Same linking rules as Google.",
   public=True, body="FacebookLoginRequest", errors=("400", "401", "429"))

# Storefront: guests and public forms
op("post", "/api/v1/store/orders", "Storefront", "guestCheckout", "Checkout without an account",
   "Public. Same pricing, promotions, coupon, shipping and stock rules as signed-in checkout. email is required. The guest is a customer "
   "without a login found again by phone. Answers the tracking view of the order. Rate limited per client.",
   public=True, body="CheckoutRequest", ok="201", errors=("400", "409", "429"))
op("post", "/api/v1/store/orders/pay", "Storefront", "guestPay", "Pay a guest order online",
   "Public. Order code and phone must match the order, like tracking. Answers the VNPay/MoMo payUrl to send the browser to.",
   public=True, body="GuestPayRequest", errors=("400", "404", "409", "429"), response=None)
op("post", "/api/v1/store/shipping/quote", "Storefront", "shippingQuote", "Estimate the shipping fee",
   "Public. Live GHTK fee for the address when the method's carrier is GHTK and GHTK is configured; otherwise the method's base fee. "
   "The free-shipping threshold applies.", public=True, body="ShippingQuoteRequest", errors=("400", "429"))
op("get", "/api/v1/store/search/suggest", "Storefront", "searchSuggestions", "Search box suggestions",
   "Public. Up to 6 on-sale products, and categories/brands whose name contains the words (accents ignored). Fewer than 2 characters answers empty lists.",
   public=True, params=[query("q", {"type": "string", "maxLength": 100})], errors=("400",))
op("post", "/api/v1/store/stock-alerts", "Storefront", "subscribeStockAlert", "Email me when it is back",
   "Public (a signed-in customer is linked). Only for a sold-out size of an on-sale product (409 VARIANT_IN_STOCK otherwise). "
   "One open request per size and email; at most 30 open per email. Rate limited per client.",
   public=True, body="StockAlertRequest", ok="201", errors=("400", "404", "409", "429"))

# Signed-in customer
op("post", "/api/v1/me/orders/{id}/pay", "Storefront", "customerPay", "Pay my order online",
   ME + " Reuses the pending payment of that method or replaces a pending payment of another method, then answers the gateway payUrl. "
   "409 ORDER_ALREADY_PAID / ORDER_CANCELLED; 400 PAYMENT_GATEWAY_UNAVAILABLE.",
   params=[path_param("id")], body="PayRequest", errors=("400", "404", "409"))
op("get", "/api/v1/me/returns", "Storefront", "customerReturns", "My return and exchange requests", ME,
   params=[query("orderId", UUID), query("page", {"type": "integer", "minimum": 0, "default": 0}),
           query("size", {"type": "integer", "minimum": 1, "maximum": 50, "default": 20})], errors=("400", "404"))
op("post", "/api/v1/me/returns", "Storefront", "customerRequestReturn", "Ask for a return or exchange",
   ME + " Same rules as staff-entered returns: delivered order, return window, unreturned quantity, exchange to a variant of the same product at the same price.",
   body="CustomerReturnRequest", ok="201", errors=("400", "404", "409"))
op("get", "/api/v1/me/returns/{id}", "Storefront", "customerReturn", "One of my return requests", ME,
   params=[path_param("id")], errors=("404",))
op("post", "/api/v1/me/returns/{id}/cancel", "Storefront", "customerCancelReturn", "Withdraw my return request",
   ME + " Only while REQUESTED; 409 RETURN_NOT_CANCELLABLE afterwards.", params=[path_param("id")], errors=("404", "409"))
op("get", "/api/v1/me/orders/{orderId}/items/{itemId}/exchange-options", "Storefront", "exchangeOptions", "What an item can be exchanged for",
   ME + " Other variants of the same product at the price paid, with stock in the order's warehouse.",
   params=[path_param("orderId"), path_param("itemId")], errors=("404",))
op("get", "/api/v1/me/cart", "Storefront", "customerCart", "My saved cart", ME + " Lines carry live names, prices and stock.", errors=("404",))
op("put", "/api/v1/me/cart", "Storefront", "saveCustomerCart", "Replace my saved cart",
   ME + " Duplicate variants are summed; unknown or deleted variants are skipped.", body="CartRequest", errors=("400", "404"))
op("post", "/api/v1/me/uploads/images", "File", "uploadCustomerImage", "Upload a photo for a review or return",
   "Requires CUSTOMER role. JPEG, PNG, WebP or GIF up to 5 MB, detected from the file signature. Rate limited per client.",
   body={"multipart/form-data": {"schema": obj({"file": {"type": "string", "format": "binary"}}, ["file"])}}, ok="201", errors=("400", "429"))

# Payments
op("get", "/api/v1/payments/vnpay/ipn", "Payments", "vnpayIpn", "VNPay IPN",
   "Called by VNPay (register this URL in the VNPay merchant portal). Verifies the HMAC-SHA512 signature and amount, settles the payment once and "
   "answers {RspCode, Message}: 00 confirmed, 01 unknown reference, 02 already confirmed, 04 wrong amount, 97 bad signature, 99 error.",
   public=True, params=[query("vnp_TxnRef", S), query("vnp_SecureHash", S)], errors=(),
   response=obj({"RspCode": S, "Message": S}))
op("post", "/api/v1/payments/momo/ipn", "Payments", "momoIpn", "MoMo IPN",
   "Called by MoMo at PAYMENT_CALLBACK_BASE_URL/api/v1/payments/momo/ipn. Verifies the HMAC-SHA256 signature and amount, settles once; 204 when accepted, 400 otherwise.",
   public=True, body={"application/json": {"schema": {"type": "object", "additionalProperties": True}}}, ok="204", errors=("400",))
op("post", "/api/v1/payments/{gateway}/return", "Payments", "paymentReturn", "Result after the shopper returns from the gateway",
   "Public. The shop's /shop/payment/{gateway} page forwards the query parameters the gateway appended. Verified like the IPN and settles the "
   "payment if the IPN has not yet (whichever arrives first wins).",
   public=True, params=[{"name": "gateway", "in": "path", "required": True, "schema": {"type": "string", "enum": ["vnpay", "momo"]}}],
   body={"application/json": {"schema": {"type": "object", "additionalProperties": {"type": "string"}}}}, errors=("400", "429"))
op("get", "/api/v1/payments/gateways", "Payments", "paymentGateways", "Online gateway status",
   "Requires SETTINGS_PAYMENT_READ. Whether VNPay and MoMo keys are configured, and the IPN URLs to register.", errors=())

# Carrier
op("get", "/api/v1/admin/carriers/ghtk", "Fulfillment", "ghtkStatus", "GHTK status", "Requires SHIPMENT_READ. Whether the GHTK token and pick-up address are configured.", errors=())
op("post", "/api/v1/admin/carriers/ghtk/shipments", "Fulfillment", "ghtkShip", "Hand a confirmed order to GHTK",
   "Requires SHIPMENT_WRITE. Creates the GHTK order (shop pays the fee, GHTK collects the unpaid balance) and records the shipment with GHTK's label as tracking code. "
   "409 CARRIER_NOT_CONFIGURED / CARRIER_REJECTED / CARRIER_UNAVAILABLE / ORDER_NOT_CONFIRMED.",
   body="CarrierShipmentRequest", ok="201", errors=("400", "404", "409"))
op("post", "/api/v1/carriers/ghtk/webhook", "Fulfillment", "ghtkWebhook", "GHTK status callback",
   "Called by GHTK at .../api/v1/carriers/ghtk/webhook?hash=GHTK_WEBHOOK_SECRET with label_id and status_id (form or JSON). Moves the shipment through "
   "the normal workflow: 3 SHIPPED, 4/10 IN_TRANSIT, 5/6 DELIVERED, 9 FAILED, 11/21 RETURNED, -1 CANCELLED. Unknown labels are ignored.",
   public=True, params=[query("hash", S, True)],
   body={"application/x-www-form-urlencoded": {"schema": obj({"label_id": S, "status_id": S, "reason": S})},
         "application/json": {"schema": obj({"label_id": S, "status_id": S, "reason": S})}}, errors=("400",))

# Admin
op("get", "/api/v1/admin/stock-alerts", "Inventory", "stockAlerts", "Open back-in-stock requests",
   "Requires STOCK_ALERT_READ. Grouped by variant, most wanted first.", params=[query("limit", {"type": "integer", "minimum": 1, "maximum": 500, "default": 100})],
   errors=("400",))
for method, summary, operation_id, extra in (("get", "Product size chart", "productSizeChart", {}),
                                             ("put", "Save product size chart", "saveProductSizeChart", {"body": "SizeChart"}),
                                             ("delete", "Remove product size chart", "deleteProductSizeChart", {})):
    op(method, "/api/v1/admin/products/{id}/size-chart", "Catalog", operation_id, summary,
       ("Requires PRODUCT_READ. data is null when the product has none." if method == "get" else
        "Requires PRODUCT_WRITE. Every row needs one cell per column (400 SIZE_CHART_SHAPE)."),
       params=[path_param("id")], errors=("400", "404"), **extra)

for name, description in (("Payments", "Online payments through VNPay and MoMo"),):
    if all(t["name"] != name for t in spec["tags"]):
        spec["tags"].append({"name": name, "description": description})
for name in ("Storefront", "Auth", "Fulfillment", "Inventory", "Catalog", "File"):
    if all(t["name"] != name for t in spec["tags"]):
        spec["tags"].append({"name": name})

spec_path.write_text(json.dumps(spec, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print("Documented", sum(len(v) for v in paths.values()), "operations")
