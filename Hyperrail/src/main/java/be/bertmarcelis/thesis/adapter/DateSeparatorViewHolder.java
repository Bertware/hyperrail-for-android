/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package be.bertmarcelis.thesis.adapter;

import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.TextView;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import be.bertmarcelis.thesis.R;

public class DateSeparatorViewHolder extends RecyclerView.ViewHolder {

    private final TextView vDateText;

    public void bind(DateTime dateTime){
        DateTimeFormatter df = DateTimeFormat.forPattern("EEE dd MMMMMMMM yyyy");
        vDateText.setText(df.print(dateTime));
    }

    DateSeparatorViewHolder(View view) {
        super(view);
        vDateText = view.findViewById(R.id.text_date);
    }
}