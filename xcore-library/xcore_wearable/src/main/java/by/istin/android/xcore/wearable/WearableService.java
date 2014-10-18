package by.istin.android.xcore.wearable;

import android.content.ContentValues;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.data.FreezableUtils;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.List;
import java.util.concurrent.TimeUnit;

import by.istin.android.xcore.Core;
import by.istin.android.xcore.callable.ISuccess;
import by.istin.android.xcore.provider.ModelContract;
import by.istin.android.xcore.source.DataSourceRequest;
import by.istin.android.xcore.utils.BytesUtils;
import by.istin.android.xcore.utils.Log;
import by.istin.android.xcore.utils.StringUtil;

/**
 * Created by Uladzimir_Klyshevich on 9/15/2014.
 */
public class WearableService extends WearableListenerService {

    private static final String START_ACTIVITY_PATH = "/start-activity";
    private static final String DATA_ITEM_RECEIVED_PATH = "/data-item-received";

    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {
        Log.xd(this, "onDataChanged: " + dataEvents);
        final List<DataEvent> events = FreezableUtils
                .freezeIterable(dataEvents);

        final GoogleApiClient googleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .build();

        ConnectionResult connectionResult =
                googleApiClient.blockingConnect(60, TimeUnit.SECONDS);

        if (!connectionResult.isSuccess()) {
            Log.xe(this, "Failed to connect to GoogleApiClient.");
            return;
        }

        // Loop through the events and send a message
        // to the node that created the data item.
        for (DataEvent event : events) {
            String lastPathSegment = event.getDataItem().getUri().getLastPathSegment();
            if (!lastPathSegment.equals(WearableContract.URI_EXECUTE_SEGMENT)) {
                continue;
            }
            DataItem dataItem = event.getDataItem();
            DataMapItem dataMapItem = DataMapItem.fromDataItem(dataItem);
            final DataMap dataMap = dataMapItem.getDataMap();

            String dataSourceKey = dataMap.getString(WearableContract.PARAM_DATA_SOURCE_KEY);
            String processorKey = dataMap.getString(WearableContract.PARAM_PROCESSOR_KEY);
            String dataSourceRequestAsString = dataMap.getString(WearableContract.PARAM_DATA_SOURCE_REQUEST);
            DataSourceRequest dataSourceRequest = ModelContract.getDataSourceRequestFromUriParam(dataSourceRequestAsString);
            String[] selectionArgs = dataMap.getStringArray(WearableContract.PARAM_SELECTION_ARGS_KEY);
            String uriAsString = dataMap.getString(WearableContract.PARAM_RESULT_URI_KEY);
            Uri resultQueryUri = StringUtil.isEmpty(uriAsString) ? null : Uri.parse(uriAsString);

            Core.ExecuteOperationBuilder executeOperationBuilder = new Core.ExecuteOperationBuilder();
            executeOperationBuilder
                    .setDataSourceKey(dataSourceKey)
                    .setProcessorKey(processorKey)
                    .setDataSourceRequest(dataSourceRequest)
                    .setResultQueryUri(resultQueryUri)
                    .setSelectionArgs(selectionArgs)
                    .setDataSourceServiceListener(new Core.SimpleDataSourceServiceListener() {

                        @Override
                        public void onError(Exception exception) {
                            super.onError(exception);
                            Log.xd(WearableService.this, "onError " + exception);
                            //TODO send to wearable
                        }

                        @Override
                        public void onCached(Bundle resultData) {
                            super.onCached(resultData);
                            Log.xd(WearableService.this, "onCached " + resultData);
                        }

                        @Override
                        public void onDone(Bundle resultData) {
                            Log.xd(WearableService.this, "onDone " + resultData);
                        }
                    }).setSuccess(new ISuccess() {
                @Override
                public void success(Object result) {
                    Log.xd(WearableService.this, "success " + result);
                    PutDataMapRequest putDataMapRequest = PutDataMapRequest.create(WearableContract.URI_RESULT);
                    DataMap dataMapResult = putDataMapRequest.getDataMap();
                    dataMapResult.putAll(dataMap);
                    if (result == null) {
                        dataMapResult.putInt(WearableContract.PARAM_RESULT_TYPE, WearableContract.TYPE_UNKNOWN);
                    } else if (result instanceof ContentValues) {
                        dataMapResult.putByteArray(WearableContract.PARAM_RESULT, BytesUtils.toByteArray((ContentValues)result));
                        dataMapResult.putInt(WearableContract.PARAM_RESULT_TYPE, WearableContract.TYPE_CONTENT_VALUES);
                    } else if (result instanceof ContentValues[]) {
                        dataMapResult.putByteArray(WearableContract.PARAM_RESULT, BytesUtils.arrayToByteArray((ContentValues[]) result));
                        dataMapResult.putInt(WearableContract.PARAM_RESULT_TYPE, WearableContract.TYPE_CONTENT_VALUES_ARRAY);
                    } else if (result instanceof Bitmap) {
                        dataMapResult.putAsset(WearableContract.PARAM_RESULT, toAsset((Bitmap)result));
                        dataMapResult.putInt(WearableContract.PARAM_RESULT_TYPE, WearableContract.TYPE_ASSET);
                    } else if (result instanceof Serializable) {
                        dataMapResult.putByteArray(WearableContract.PARAM_RESULT, BytesUtils.toByteArray((Serializable) result));
                        dataMapResult.putInt(WearableContract.PARAM_RESULT_TYPE, WearableContract.TYPE_SERIALIZABLE);
                    } else {
                        dataMapResult.putInt(WearableContract.PARAM_RESULT_TYPE, WearableContract.TYPE_UNKNOWN);
                        Log.xe(WearableService.this, "Unsupported result of processor. Result need to be ContentValues, ContentValues[] or Serializable");
                    }
                    if (googleApiClient.isConnected()) {
                        Wearable.DataApi.putDataItem(googleApiClient, putDataMapRequest.asPutDataRequest())
                                .setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
                                    @Override
                                    public void onResult(DataApi.DataItemResult dataItemResult) {
                                        if (!dataItemResult.getStatus().isSuccess()) {
                                            Log.xe(WearableService.this, "result did not delivered");
                                        }
                                    }
                                });
                    }
                }
            });
            Core.get(this).execute(executeOperationBuilder.build());

            Log.xd(this, "new wearable request: " + dataSourceKey + " " + processorKey + " " + selectionArgs + " " + resultQueryUri + " " + dataSourceRequest.toUriParams());

            Uri dataItemUri = dataItem.getUri();
            // Get the node id from the host value of the URI
            String nodeId = dataItemUri.getHost();
            // Set the data of the message to be the bytes of the URI.
            byte[] payload = dataItemUri.toString().getBytes();

            // Send the RPC
            Wearable.MessageApi.sendMessage(googleApiClient, nodeId,
                    DATA_ITEM_RECEIVED_PATH, payload);
        }
    }

    private static Asset toAsset(Bitmap bitmap) {
        ByteArrayOutputStream byteStream = null;
        try {
            byteStream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteStream);
            return Asset.createFromBytes(byteStream.toByteArray());
        } finally {
            if (null != byteStream) {
                try {
                    byteStream.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }
    }

}
