package ec.edu.utn.com.example.remotemonitoringapp;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.NumberPicker;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class SettingsActivity extends AppCompatActivity {

    private static final String ETIQUETA = "SettingsActivity";
    private static final String PREFS_NAME = "MonitoringPrefs";
    private static final String KEY_DAYS = "collection_days";
    private static final String KEY_START_HOUR = "start_hour";
    private static final String KEY_END_HOUR = "end_hour";

    private CheckBox[] dayCheckBoxes;
    private NumberPicker npStartHour, npEndHour;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        // Inicializar componentes de la UI
        dayCheckBoxes = new CheckBox[]{
                findViewById(R.id.cb_sunday),
                findViewById(R.id.cb_monday),
                findViewById(R.id.cb_tuesday),
                findViewById(R.id.cb_wednesday),
                findViewById(R.id.cb_thursday),
                findViewById(R.id.cb_friday),
                findViewById(R.id.cb_saturday)
        };

        npStartHour = findViewById(R.id.np_start_hour);
        npEndHour = findViewById(R.id.np_end_hour);

        // Configurar NumberPickers
        npStartHour.setMinValue(0);
        npStartHour.setMaxValue(23);
        npEndHour.setMinValue(0);
        npEndHour.setMaxValue(23);

        // Cargar configuraciones actuales
        loadPreferences();

        // Configurar botones
        Button btnSave = findViewById(R.id.btn_save);
        Button btnCancel = findViewById(R.id.btn_cancel);

        btnSave.setOnClickListener(v -> savePreferences());
        btnCancel.setOnClickListener(v -> finish());
    }

    private void loadPreferences() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean[] days = new boolean[7];
        String daysString = prefs.getString(KEY_DAYS, "1111111"); // Por defecto todos los días activos
        for (int i = 0; i < days.length && i < daysString.length(); i++) {
            days[i] = daysString.charAt(i) == '1';
            dayCheckBoxes[i].setChecked(days[i]);
        }

        int startHour = prefs.getInt(KEY_START_HOUR, 0); // Por defecto 0
        int endHour = prefs.getInt(KEY_END_HOUR, 23);   // Por defecto 23
        npStartHour.setValue(startHour);
        npEndHour.setValue(endHour);
    }

    private void savePreferences() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        StringBuilder daysString = new StringBuilder();
        for (CheckBox cb : dayCheckBoxes) {
            daysString.append(cb.isChecked() ? "1" : "0");
        }

        int startHour = npStartHour.getValue();
        int endHour = npEndHour.getValue();

        if (startHour > endHour) {
            Toast.makeText(this, "La hora de inicio debe ser menor o igual a la hora de fin", Toast.LENGTH_LONG).show();
            return;
        }

        if (daysString.toString().equals("0000000")) {
            Toast.makeText(this, "Debe seleccionar al menos un día", Toast.LENGTH_LONG).show();
            return;
        }

        editor.putString(KEY_DAYS, daysString.toString());
        editor.putInt(KEY_START_HOUR, startHour);
        editor.putInt(KEY_END_HOUR, endHour);
        editor.apply();

        Toast.makeText(this, "Configuración guardada", Toast.LENGTH_SHORT).show();
        setResult(RESULT_OK);
        finish();
    }
}