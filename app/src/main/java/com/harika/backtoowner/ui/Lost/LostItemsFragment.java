package com.harika.backtoowner.ui.Lost;

import static android.app.Activity.RESULT_OK;
import static android.content.ContentValues.TAG;

import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.TimePickerDialog;
import android.content.Intent;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.harika.backtoowner.Utility;
import com.google.firebase.firestore.DocumentReference;
import com.harika.backtoowner.R;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;


public class LostItemsFragment extends DialogFragment {
    private ImageButton datePickerButton;
    private ImageButton timePickerButton;
    private TextView dateEdit;
    private TextView timeEdit;
    private Spinner categorySpinner;
    ImageView image;
    Button upload;

    Uri imageUri;
    EditText description;
    private EditText location;

    final int REQ_CODE = 1000;

    private int mYear, mMonth, mDay, mHour, mMinute;
    String date = null;
    String time = null;

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        return super.onCreateDialog(savedInstanceState);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_lost_items, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        description = view.findViewById(R.id.description);
        datePickerButton = view.findViewById(R.id.datePickerButton);
        timePickerButton = view.findViewById(R.id.timePickerButton);
        datePickerButton.setOnClickListener(v -> showDatePicker());
        timePickerButton.setOnClickListener(v -> showTimePicker());
        dateEdit = view.findViewById(R.id.selectedDateEditText);
        timeEdit = view.findViewById(R.id.selectedTimeEditText);
        location = view.findViewById(R.id.location);
        image = view.findViewById(R.id.previewImage);

        categorySpinner = view.findViewById(R.id.categorySpinner);

        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(requireContext(),
                R.array.categories_array, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        categorySpinner.setAdapter(adapter);

        final String[] selectedCategory = new String[1];
        categorySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedCategory[0] = categorySpinner.getItemAtPosition(position).toString();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        upload = view.findViewById(R.id.uploadImageButton);
        upload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent iGallery = new Intent(Intent.ACTION_PICK);
                iGallery.setData(MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                startActivityForResult(iGallery, REQ_CODE);
            }
        });

        Button submitButton = view.findViewById(R.id.submit_button);

        submitButton.setOnClickListener(v -> {

            EditText item = view.findViewById(R.id.item_name_edittext);
            String itemName = item.getText().toString();

            if (itemName.isEmpty()) {
                Utility.showToast(getContext(), "Name cannot be empty");
                return;
            }
            if (selectedCategory[0] == null) {
                Utility.showToast(getContext(), "Please select a category");
                return;
            }
            if (date == null) {
                showDatePicker();
                return;
            }
            if (time == null) {
                showTimePicker();
                return;
            }
            String loc = location.getText().toString();
            if (loc.isEmpty()) {
                Utility.showToast(getContext(), "Please provide location");
                return;
            }
            String desc = description.getText().toString();
            if (desc.isEmpty()) {
                Utility.showToast(getContext(), "Please add description");
                return;
            }

            LostItems lostItem = new LostItems();
            lostItem.setItemName(itemName);
            lostItem.setCategory(selectedCategory[0]);
            lostItem.setDateLost(date);
            lostItem.setTimeLost(time);
            lostItem.setLocation(loc);
            lostItem.setDescription(desc);

            FirebaseAuth mAuth = FirebaseAuth.getInstance();
            FirebaseUser currentUser = mAuth.getCurrentUser();

            // 🔍 DEBUG: check if user is logged in
            if (currentUser == null) {
                Log.e(TAG, "DEBUG: currentUser is NULL — user not logged in");
                Toast.makeText(getContext(), "User not logged in!", Toast.LENGTH_LONG).show();
                return;
            }

            Log.d(TAG, "DEBUG: currentUser email = " + currentUser.getEmail());

            String userEmail = currentUser.getEmail();
            String userID = currentUser.getUid();

            FirebaseFirestore db = FirebaseFirestore.getInstance();
            Log.d(TAG, "STEP 2: Starting Firestore query");
            db.collection("users")
                    .whereEqualTo("email", userEmail)
                    .get()
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            // 🔍 DEBUG: how many user docs found
                            Log.d(TAG, "STEP 3: Query successful");
                            Log.d(TAG, "DEBUG: user query success, docs found = " + task.getResult().size());

                            if (task.getResult().isEmpty()) {
                                // No user doc found — save anyway with just email + uid
                                Log.w(TAG, "DEBUG: No user doc in 'users' for email: " + userEmail);
                                lostItem.setEmail(userEmail);
                                lostItem.setUserId(userID);
                                saveItemToFirebase(lostItem);
                                return;
                            }

                            for (QueryDocumentSnapshot document : task.getResult()) {
                                String userName = document.getString("name");
                                String userPhone = document.getString("phone");
                                Log.d(TAG, "DEBUG: userName=" + userName + ", userPhone=" + userPhone);
                                lostItem.setOwnerName(userName);

                                if (userPhone != null && !userPhone.isEmpty()) {
                                    try {
                                        lostItem.setPhnum(Long.parseLong(userPhone));
                                    } catch (NumberFormatException e) {
                                        Log.e(TAG, "Invalid phone format: " + userPhone);
                                    }
                                } else {
                                    Log.e(TAG, "Phone missing — saving without phone");
                                }

                                lostItem.setEmail(userEmail);
                                lostItem.setUserId(userID);
                            }
                            Log.d(TAG, "STEP 4: Saving without image");

                            lostItem.setImageURI(null); // no image for now

                            saveItemToFirebase(lostItem);


                        } else {
                            Log.e(TAG, "DEBUG: user query FAILED", task.getException());
                            Toast.makeText(getContext(), "Error: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });
        });
    }

    private String generateImageName() {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        return "image_" + timeStamp + ".jpg";
    }

    private void saveItemToFirebase(LostItems item) {
        Log.d(TAG, "STEP 5: Inside save function");
        Log.d(TAG, "DEBUG: saveItemToFirebase called");

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        DocumentReference documentReference = db.collection("lost_items").document();

        documentReference.set(item).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                Log.d(TAG, "STEP 6: SAVE SUCCESS");
                Log.d(TAG, "DEBUG: item saved successfully!");
                Toast.makeText(getContext(), "Item added", Toast.LENGTH_SHORT).show();
                dismiss();
            } else {
                Log.e(TAG, "DEBUG: save FAILED", task.getException());
                Toast.makeText(getContext(), "Error: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK && requestCode == REQ_CODE) {
            if (data != null && data.getData() != null) {
                imageUri = data.getData();
                upload.setText("Image added");
                image.setImageURI(imageUri);
            }
        }
    }

    private void showDatePicker() {
        final Calendar c = Calendar.getInstance();
        mYear = c.get(Calendar.YEAR);
        mMonth = c.get(Calendar.MONTH);
        mDay = c.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog datePickerDialog = new DatePickerDialog(requireContext(),
                (view, year, monthOfYear, dayOfMonth) -> {
                    mYear = year;
                    mMonth = monthOfYear;
                    mDay = dayOfMonth;
                    updateDateButton();
                }, mYear, mMonth, mDay);
        datePickerDialog.show();
        updateDateButton();
    }

    private void showTimePicker() {
        final Calendar c = Calendar.getInstance();
        mHour = c.get(Calendar.HOUR_OF_DAY);
        mMinute = c.get(Calendar.MINUTE);

        TimePickerDialog timePickerDialog = new TimePickerDialog(requireContext(),
                (view, hourOfDay, minute) -> {
                    mHour = hourOfDay;
                    mMinute = minute;
                    updateTimeButton();
                }, mHour, mMinute, false);
        timePickerDialog.show();
        updateTimeButton();
    }

    private String updateDateButton() {
        String date = mDay + "/" + (mMonth + 1) + "/" + mYear;
        dateEdit.setText(date);
        this.date = date;
        return date;
    }

    private String updateTimeButton() {
        String AM_PM;
        if (mHour < 12) {
            AM_PM = "AM";
        } else {
            AM_PM = "PM";
        }
        int hour = mHour % 12;
        if (hour == 0) {
            hour = 12;
        }
        // fixed: pad minutes to 2 digits
        String time = String.format(Locale.getDefault(), "%d:%02d %s", hour, mMinute, AM_PM);
        timeEdit.setText(time);
        this.time = time;
        return time;
    }

}