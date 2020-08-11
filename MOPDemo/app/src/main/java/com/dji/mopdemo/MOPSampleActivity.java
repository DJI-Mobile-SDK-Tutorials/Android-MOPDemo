package com.dji.mopdemo;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatCheckBox;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

import dji.log.DJILog;
import dji.mop.common.Pipeline;
import dji.mop.common.PipelineDeviceType;
import dji.mop.common.TransmissionControlType;
import dji.sdk.base.BaseProduct;
import dji.sdk.flightcontroller.FlightController;
import dji.sdk.payload.Payload;
import dji.sdk.products.Aircraft;
import dji.sdk.sdkmanager.DJISDKManager;

public class MOPSampleActivity extends AppCompatActivity {

    private EditText keyEt;
    private RecyclerView pipelineRc;
    private RadioGroup typeGroup;
    private AppCompatCheckBox safeBox;
    private PipelineAdapter adapter;
    private AppCompatCheckBox logBox;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mopsample);
        keyEt = findViewById(R.id.et_channel_key);
        pipelineRc = findViewById(R.id.rc_pipeline);
        typeGroup = findViewById(R.id.rg_mop_type);
        safeBox = findViewById(R.id.cb_reliable);
        logBox = findViewById(R.id.cb_log);

        adapter = new PipelineAdapter(this, new ArrayList<>());
        pipelineRc.setLayoutManager(new LinearLayoutManager(this));
        pipelineRc.setAdapter(adapter);
        pipelineRc.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));

        getFlightController().savePipelinesLog(logBox.isChecked());

        logBox.setOnCheckedChangeListener((buttonView, isChecked) -> getFlightController().savePipelinesLog(isChecked));

        findViewById(R.id.btn_create).setOnClickListener(v -> {
            String key = keyEt.getText().toString().trim();
            if (TextUtils.isEmpty(key)) {
                Toast.makeText(v.getContext(), "please input channel_id", Toast.LENGTH_SHORT).show();
                return;
            }
            PipelineDeviceType type = getType(typeGroup.getCheckedRadioButtonId());
            TransmissionControlType transferType = safeBox.isChecked() ? TransmissionControlType.STABLE : TransmissionControlType.PUSH;

            switch (type) {
                case PAYLOAD:
                    Payload payload = getPayload();
                    if (payload == null) {
                        Toast.makeText(MOPSampleActivity.this, "payload == null", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    payload.getPipelines().connect(Tools.getInt(key, 0), transferType, error -> {
                        if (error == null) {
                            Pipeline p = payload.getPipelines().getPipeline(Tools.getInt(key, 0));
                            if (p != null) {
                                DJILog.d("MopActivity", "connect success: " + p.toString());
                                runOnUiThread(() -> adapter.addItem(p));
                            }
                        }
                        String tip = error == null ? "success" : error.toString();
                        runOnUiThread(() -> Toast.makeText(MOPSampleActivity.this, "connect result:" + tip, Toast.LENGTH_SHORT).show());
                    });
                    break;
                case ON_BOARD:
                    FlightController flightController = getFlightController();
                    if (flightController == null) {
                        Toast.makeText(MOPSampleActivity.this, "flightController == null", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    flightController.getPipelines().connect(Tools.getInt(key, 0), transferType, error -> {
                        if (error == null) {
                            runOnUiThread(() -> adapter.addItem(flightController.getPipelines().getPipeline(Tools.getInt(key, 0))));
                        }
                        String tip = error == null ? "success" : error.toString();
                        runOnUiThread(() -> Toast.makeText(MOPSampleActivity.this, "connect result:" + tip, Toast.LENGTH_SHORT).show());
                    });
                    break;
            }
        });
    }

    private Payload getPayload() {
        if (DJISDKManager.getInstance().getProduct() == null) {
            return null;
        }
        return DJISDKManager.getInstance().getProduct().getPayload();
    }

    private FlightController getFlightController() {
        BaseProduct product = DJISDKManager.getInstance().getProduct();
        if (product != null && product instanceof Aircraft) {
            return ((Aircraft) product).getFlightController();
        }
        return null;
    }

    private PipelineDeviceType getType(int checkedRadioButtonId) {
        switch (checkedRadioButtonId) {
            case R.id.rb_on_board:
                return PipelineDeviceType.ON_BOARD;
            case R.id.rb_payload:
                return PipelineDeviceType.PAYLOAD;
            default:
                return PipelineDeviceType.PAYLOAD;

        }
    }

}
