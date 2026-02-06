#### What's Changed
---

##### `PUT` /v1/shop/commercial-orders/{commercialOrderId}/sync

> ORDER-151 - Synchro order lines


###### Parameters:

Added: `orderCommercialReference` in `path`

Added: `customer-account-id` in `header`
> (Optional) Customer account ID in multi-account customer user.


Deleted: `commercialOrderId` in `path`
> Unique commercial order ID (REFERENCE only). Business IDs or EXTERNAL_IDs are not supported for this path.


Changed: `dj-client` in `header`
> Specifies the client type making the request. - `OPERATOR`: Platform operator user. - `ACCOUNT`: Customer account user. - `SUPPLIER`: Supplier managing orders.


Changed: `dj-api-key` in `header`
> API key required for authentication. Must be valid and linked to the requesting user.


Changed: `dj-store` in `header`
> (Optional) Store ID in multi-store environments.


Changed: `dj-store-view` in `header`
> (Optional) Store view ID in multi-store environments.


###### Return Type:

Deleted response : **204 No Content**
> No Content.<br>
> There was nothing to synchronise and no warning to report.

Deleted response : **400 Bad Request**
> Bad request (invalid input).

Deleted response : **401 Unauthorized**
> Unauthorized. Missing or invalid authentication token.

Deleted response : **403 Forbidden**
> Forbidden. The caller is not allowed to synchronise this order.

Deleted response : **409 Conflict**
> Conflict. The order cannot be synchronised in its current state (e.g., already validated).

Changed response : **200 OK**
> Order lines synchronized


* Changed content type : `application/json`

    Changed items (object):

    New optional properties:
    - `detail`
    - `id`

    * Deleted property `code` (string)
        > Functional warning code (F-W-XXX).


    * Deleted property `blocked` (boolean)
        > Indicates whether this warning prevents applying the synchronisation.


    * Changed property `id` (string)

    * Changed property `detail` (string)

Changed response : **404 Not Found**
> Order commercial not found


* Changed content type : `application/json`

    New required properties:
    - `content`
    - `errors`
    - `warnings`

    New optional properties:
    - `code`
    - `error`
    - `message`

    * Added property `content` (object)

    * Added property `errors` (array)

        Items (object):

        * Property `code` (string)

        * Property `detail` (string)

        * Property `message` (string)

    * Added property `warnings` (array)

        Items (object):

        * Property `code` (string)

        * Property `field` (string)

        * Property `message` (string)

    * Deleted property `error` (string)

    * Deleted property `code` (string)

    * Deleted property `message` (string)



### Metadata Changes
#### PUT `/v1/shop/commercial-orders/{commercialOrderId}/sync`
- **Summary**:
    - **PM** : `ORDER-223 — Synchronise a commercial order`
    - **Gen**: `ORDER-151 - Synchro order lines`
- **Description**:
    - **PM** :
> **Overview**<br> Synchronises a commercial order so it remains consistent with the current pricing, availability and configuration rules applied to its order lines.<br><br> **User scoping**<br> Only `dj-client: ACCOUNT` is allowed (customer users).<br><br> **Store scoping**<br> The store context is resolved from `dj-store` (when provided) or from the tenant default store.<br> `dj-store-view` may further refine the store view context when applicable.<br><br> **Visibility**<br> Access and visibility rules apply to the order and its content, including ownership/permissions and catalog visibility (eligible catalog views).<br><br> **Validation rules**<br> The commercial order must be editable and not validated.<br> When at least one blocking warning is detected, no change is applied.<br> This endpoint only applies to commercial orders created through an order-based checkout.<br><br> **Business rules**<br> This endpoint supports two execution contexts depending on the tenant configuration:<br> - RTP (Real Time Pricing): synchronisation relies on pricing and availability data provided by an external system outside of Djust, accessed in real time.<br> - Standard mode: synchronisation relies on persisted Djust data (catalog, offer prices, offer stock, custom fields).<br> In both contexts, offer eligibility rules apply (offer price, offer stock and supplier status, account eligibility).<br><br> **Errors**<br> This endpoint may return authentication, authorisation (permissions), validation, not found or conflict errors.<br> Standard Djust error codes apply.
    - **Gen**:
> null

#### PUT `/v1/shop/commercial-orders/{commercialOrderId}/sync` (Parameter: `dj-client`)
- **Description**:
    - **PM** : The type of client making the request. Must be set to ACCOUNT.
    - **Gen**: Specifies the client type making the request. - `OPERATOR`: Platform operator user. - `ACCOUNT`: Customer account user. - `SUPPLIER`: Supplier managing orders.

#### PUT `/v1/shop/commercial-orders/{commercialOrderId}/sync` (Parameter: `dj-api-key`)
- **Description**:
    - **PM** : API key for authentication and authorisation.
    - **Gen**: API key required for authentication. Must be valid and linked to the requesting user.

#### PUT `/v1/shop/commercial-orders/{commercialOrderId}/sync` (Parameter: `dj-store`)
- **Description**:
    - **PM** : The store identifier, if applicable.
    - **Gen**: (Optional) Store ID in multi-store environments.

#### PUT `/v1/shop/commercial-orders/{commercialOrderId}/sync` (Parameter: `dj-store-view`)
- **Description**:
    - **PM** : The specific store view identifier for multi-store configurations.
    - **Gen**: (Optional) Store view ID in multi-store environments.

#### PUT `/v1/shop/commercial-orders/{commercialOrderId}/sync`
- **Response 200 Description**:
    - **PM** : List of synchronisation warnings.

    - **Gen**: null

#### PUT `/v1/shop/commercial-orders/{commercialOrderId}/sync`
- **Response 200 items -> id Description**:
    - **PM** : External offer price id related to the warning (the most granular identifier for order lines).

    - **Gen**: null

#### PUT `/v1/shop/commercial-orders/{commercialOrderId}/sync`
- **Response 200 items -> detail Description**:
    - **PM** : Human-readable warning message (may include contextual details).

    - **Gen**: null

