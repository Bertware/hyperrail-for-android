/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package be.bertmarcelis.thesis.irail.contracts;

import android.support.annotation.NonNull;

public interface IRailSuccessResponseListener<T> {

    void onSuccessResponse(@NonNull T data, Object tag);

}
