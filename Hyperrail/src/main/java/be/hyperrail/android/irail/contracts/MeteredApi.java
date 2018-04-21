package be.hyperrail.android.irail.contracts;

import android.net.TrafficStats;
import android.support.annotation.IntDef;

/**
 * A metered API allows to get details on network usage
 */
public interface MeteredApi {

    MeteredRequest[] getMeteredRequests();

    class MeteredRequest {
        private String mTag;
        private long mBytesSent;
        private long mBytesReceived;
        private long mMsecStart;
        private long mMsecUsableResult;
        private long mMsecParsed;
        private int mResponseType;
        private long rxBbytesAtStart;
        private long txBbytesAtStart;

        public MeteredRequest() {

        }

        public String getTag() {
            return mTag;
        }

        public long getBytesSent() {
            return mBytesSent;
        }

        public long getBytesReceived() {
            return mBytesReceived;
        }

        public long getMsecStart() {
            return mMsecStart;
        }

        public long getMsecUsableResult() {
            return mMsecUsableResult;
        }

        public long getMsecParsed() {
            return mMsecParsed;
        }

        public void setTag(String tag) {
            mTag = tag;
        }

        public void setMsecStart(long msecStart) {
            mMsecStart = msecStart;
        }

        public void setMsecUsableNetworkResponse(long msecFirstByte) {
            if (mMsecUsableResult == 0) {
                mMsecUsableResult = msecFirstByte;
                rxBbytesAtStart = TrafficStats.getTotalRxBytes();
                txBbytesAtStart = TrafficStats.getTotalTxBytes();
            }
        }

        public void setMsecParsed(long msecParsed) {
            if (mMsecParsed == 0) {
                mMsecParsed = msecParsed;
                mBytesReceived = TrafficStats.getTotalRxBytes() - rxBbytesAtStart;
                mBytesSent = TrafficStats.getTotalTxBytes() - txBbytesAtStart;
            }
        }

        public int getResponseType() {
            return mResponseType;
        }

        public String getResponseTypeList() {
            String result = "";
            if ((mResponseType & 1) == 1) {
                result += "Online,";
            }
            if ((mResponseType & 2) == 2) {
                result += "Cached,";
            }
            if ((mResponseType & 4) == 4) {
                result += "Offline,";
            }
            if ((mResponseType & 8) == 8) {
                result += "Failed";
            }
            if (result.endsWith(",")) {
                result = result.substring(0, result.length() - 1);
            }
            return result;
        }

        public void setResponseType(@responseType int responseType) {
            mResponseType |= responseType;
        }

        @Override
        public String toString() {
            return mTag + "," + mMsecStart + "," + mMsecUsableResult + "," + mMsecParsed + "," + mBytesReceived + "," + mBytesSent + "," + getResponseTypeList();
        }
    }

    @IntDef({RESPONSE_ONLINE, RESPONSE_CACHED, RESPONSE_OFFLINE, RESPONSE_FAILED})
    @interface responseType {
    }

    int RESPONSE_ONLINE = 1;
    int RESPONSE_CACHED = 2;
    int RESPONSE_OFFLINE = 4;
    int RESPONSE_FAILED = 8;
}
