/*
 * Copyright 2012 The Stanford MobiSocial Laboratory
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package mobisocial.musubi.ui.fragments;

import mobisocial.musubi.R;
import mobisocial.musubi.ui.SettingsActivity;
import android.app.Dialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.text.Html;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

public class AnonymousStatsSampleDialog extends DialogFragment {

    public static AnonymousStatsSampleDialog newInstance() {
        AnonymousStatsSampleDialog frag = new AnonymousStatsSampleDialog();
        Bundle args = new Bundle();
        frag.setArguments(args);
        return frag;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Dialog d = super.onCreateDialog(savedInstanceState);
        d.setContentView(R.layout.sample_stats);
        d.setTitle("Anonymous Statistics");
        StringBuilder html = new StringBuilder();
        html.append("<html><p>Your privacy is important to us. You can help us build a better Musubi ")
            .append("by sharing anonymous usage statistics, which includes things like:</p>")
            .append("<p>")
                .append("\t&#149;Number of accounts connected<br/>")
                .append("\t&#149;Number of conversations started<br/>")
                .append("\t&#149;Number of messages sent<br/>")
                .append("\t&#149;Number of sketches drawn<br/>")
                .append("\t&#149;When you update your profile<br/>")
            .append("</p>");
	    SharedPreferences p = getActivity().getSharedPreferences(SettingsActivity.PREFS_NAME, 0);
	    if (p.getBoolean(SettingsActivity.PREF_ANONYMOUS_STATS, true)) {
            html.append("<p><b>Thank you for helping us improve Musubi!</b></p>");
	    }
        html.append("</html>");
        TextView sample_text = (TextView)d.findViewById(R.id.stat_text);
        sample_text.setText(Html.fromHtml(html.toString()));
        Button ok_button = (Button)d.findViewById(R.id.stat_ok);
        ok_button.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				dismiss();
			}
		});
        return d;
    }
}
