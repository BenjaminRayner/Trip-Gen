package com.example.tripgen;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.os.Handler;
import android.widget.TimePicker;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.tripgen.databinding.FragmentAIChatBinding;
import com.example.tripgen.databinding.FragmentFirstBinding;
import com.google.android.libraries.places.api.model.Place;
import com.google.firebase.firestore.FirebaseFirestore;
//import com.google.firebase.database.FirebaseDatabase;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CountDownLatch;

public class AIChatFragment extends Fragment {

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 123;
    private RecyclerView recyclerView;
    private EditText inputText;
    private Button sendButton;
    private ArrayList<Message> messages = new ArrayList<>();
    private MessagesAdapter messagesAdapter = new MessagesAdapter(messages);
    private FragmentAIChatBinding binding;
    private int optionSelected;

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {

        binding = FragmentAIChatBinding.inflate(inflater, container, false);
        return binding.getRoot();

    }

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        MainActivity mainActivity = (MainActivity) requireActivity();

        // Set reverse layout
        LinearLayoutManager layoutManager = new LinearLayoutManager(getContext());
        OpenAiApi chatbot =  new OpenAiApi();
        layoutManager.setStackFromEnd(true);
        binding.recyclerView.setLayoutManager(layoutManager);
        binding.recyclerView.setAdapter(messagesAdapter); // Add this line

        tempBot("initial"); // initial bot message

        GoogleApi googleApi = new GoogleApi(getActivity());

        binding.sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Add new message at the end of the list
                String userMessage = binding.inputText.getText().toString();
                //print user message
                Log.d("user message", userMessage);

                if(optionSelected == 1){ // Recommended places
                    Log.d("OptionSelected","1");
                }
                if(optionSelected == 2){ // Locate Nearby Attractions (No need to talk to chat gpt)
                    Log.d("OptionSelected","2");

                    return;
                }
                if(optionSelected == 3){ // Discover Places
                    Log.d("OptionSelected","3");
                }
                if(!userMessage.isEmpty() && optionSelected == 1) {
                    messages.add(new Message(userMessage,"",null, Message.TYPE_USER));
                    binding.inputText.setText("");
                    messagesAdapter.notifyDataSetChanged();
                    binding.recyclerView.smoothScrollToPosition(messages.size() - 1); // This will automatically scroll to the end when a new message is added
                    hideKeyboard(v);
                    messages.add(new Message("Please give me few seconds while I gather places for you","",null, Message.TYPE_AI));
                    messagesAdapter.notifyDataSetChanged();
                    binding.recyclerView.smoothScrollToPosition(messages.size() - 1);
                    chatbot.getRecommendedPlaces(userMessage).thenAccept(placeList -> {
                        if(placeList.isEmpty()){
                            messages.add(new Message("I am sorry, I couldn't find any place for the given input","",null, Message.TYPE_AI));
                            Activity activity = getActivity();
                            if (activity != null) {
                                activity.runOnUiThread(() -> messagesAdapter.notifyDataSetChanged());
                                binding.recyclerView.smoothScrollToPosition(messages.size() - 1);
                            }
                        }
                        for (String place : placeList) {


                            googleApi.getPictureOfLocationToManipulate(place, new GoogleApi.FetchPictureCallback() {
                                @Override
                                public void onPictureFetched(Bitmap bitmap, Place place1) {
                                    // Do whatever you need with the bitmap here
                                    messages.add(new Message(place,"",bitmap,Message.TYPE_LISTS));
                                    Log.d("Debug", "Bitmap width: " + bitmap.getWidth() + ", height: " + bitmap.getHeight());

                                    // Notify the adapter on the main/UI thread
                                    Activity activity = getActivity();
                                    if (activity != null) {
                                        activity.runOnUiThread(() -> messagesAdapter.notifyDataSetChanged());
                                        binding.recyclerView.smoothScrollToPosition(messages.size() - 1);
                                    }
                                }

                                @Override
                                public void onFetchFailure(Exception exception) {
                                    // Handle the exception here
                                    Log.d("Debug", "Error while getting the image");
                                }
                            });
                        }
                    });

                }

                if(!userMessage.isEmpty() && optionSelected == 3) {
                    messages.add(new Message(userMessage,"",null, Message.TYPE_USER));
                    binding.inputText.setText("");
                    messagesAdapter.notifyDataSetChanged();
                    binding.recyclerView.smoothScrollToPosition(messages.size() - 1); // This will automatically scroll to the end when a new message is added
                    hideKeyboard(v);
                    tempBot("AiTyping");
                    Activity activity = getActivity();
                    chatbot.getPlaceDetail(userMessage).thenAccept(details -> {
                        if(details.isEmpty()){
                            messages.add(new Message("I am sorry, I am not able to answer this question","",null, Message.TYPE_AI));
                            if (activity != null) {
                                activity.runOnUiThread(() -> messagesAdapter.notifyDataSetChanged());
                                binding.recyclerView.smoothScrollToPosition(messages.size() - 1);
                            }
                        }
                        messages.remove(messages.size()-1);
                        if (activity != null) {
                            activity.runOnUiThread(() -> messagesAdapter.notifyDataSetChanged());
                            binding.recyclerView.smoothScrollToPosition(messages.size() - 1);
                        }


                        Log.d("OpenAiApi", "Recommended place: " + details);
                        messages.add(new Message(details,"",null,Message.TYPE_AI));


                        // Notify the adapter on the main/UI thread

                        if (activity != null) {
                            activity.runOnUiThread(() -> messagesAdapter.notifyDataSetChanged());
                            binding.recyclerView.smoothScrollToPosition(messages.size() - 1);
                        }
                    });
                }
            }
        });

        binding.recyclerView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                hideKeyboard(v);
                return false;
            }
        });




        messagesAdapter.setOnOptionClickListener(new MessagesAdapter.OnOptionClickListener(){
            @Override
            public void onOptionClick(int position){
                Message clickedOption = messages.get(position);
                //print clicked option
                optionSelected = position;

                if(position == 2){

                    messages.add(new Message("I am finding nearby attractions for you","",null, Message.TYPE_AI));
                    messagesAdapter.notifyDataSetChanged();
                    binding.recyclerView.smoothScrollToPosition(messages.size() - 1); // This will automatically scroll to the end when a new message is added
                    googleApi.makeNearbyPlaceRequestToManipulate(new GoogleApi.FetchPlaceArrayListCallback(){

                        @Override
                        public void onPlaceArrayListFetched(ArrayList<Place> places){
                            for (Place place : places) {

                                String placeName = place.getName();

                                googleApi.getPictureOfLocationToManipulate(placeName, new GoogleApi.FetchPictureCallback() {
                                    @Override
                                    public void onPictureFetched(Bitmap bitmap, Place place1) {
                                        // Do whatever you need with the bitmap here
                                        messages.add(new Message(placeName,"",bitmap,Message.TYPE_LISTS));
                                        Log.d("Debug", "Bitmap width: " + bitmap.getWidth() + ", height: " + bitmap.getHeight());

                                        // Notify the adapter on the main/UI thread
                                        Activity activity = getActivity();
                                        if (activity != null) {
                                            activity.runOnUiThread(() -> {
                                                messagesAdapter.notifyDataSetChanged();
                                                binding.recyclerView.smoothScrollToPosition(messages.size() - 1); // This will automatically scroll to the end when a new message is added
                                            });
                                        }
                                    }

                                    @Override
                                    public void onFetchFailure(Exception exception) {
                                        // Handle the exception here
                                        Log.d("Debug", "Error while getting the image");
                                    }
                                });
                                Log.i("Place", "Nearby-Place Name: " + place.getName());
                                Log.i("Place", "Nearby-Place Address: " + place.getAddress());
                            }
                        }
                        @Override
                        public void onFetchPlaceFailure(Exception exception){
                            Log.d("Debug", "Error while getting near by locations");

                        }

                    });
                }else {

                    //remove options
//                    Iterator<Message> iter = messages.iterator();
//                    while (iter.hasNext()) {
//                        Message m = iter.next();
//                        if (m.getType() == Message.TYPE_OPTIONS && m != clickedOption) {
//                            iter.remove();
//                        }
//                    }
                    messagesAdapter.notifyDataSetChanged();
                    tempBot("placeToVisit");
                }

            }
        });


        messagesAdapter.setOnPlaceClickListener(new MessagesAdapter.OnPlaceClickListener() {
            @Override
            public void onPlaceClick(String placeName) {
                // Handle place click
                TimePickerDialog.OnTimeSetListener onTimeSetListener = new TimePickerDialog.OnTimeSetListener() {

                    public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
                        int startTimeHour = hourOfDay;
                        int startTimeMin = minute;
                        // End Time dialog

                        TimePickerDialog.OnTimeSetListener onTimeSetListener = new TimePickerDialog.OnTimeSetListener() {
                            public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
                                int endTimeHour = hourOfDay;
                                int endTimeMin = minute;
                                // add activity to itinerary
                                System.out.println(startTimeHour); // start time hour
                                System.out.println(startTimeMin); // start time min
                                System.out.println(endTimeHour); // end time hour
                                System.out.println(endTimeMin); // end time min
                                Toast.makeText(getContext(), "Place: " + placeName + " added to itinerary", Toast.LENGTH_SHORT).show();


                                // add activity to itinerary
                                com.example.tripgen.Activity activity = new com.example.tripgen.Activity(placeName, startTimeHour + startTimeMin, startTimeHour, startTimeMin, endTimeHour, endTimeMin);
                                db.collection("Trips").document(mainActivity.currentTrip).collection(mainActivity.currentDay).add(activity);
                            }
                        };
                        TimePickerDialog timePickerDialog = new TimePickerDialog(getContext(), onTimeSetListener, startTimeHour, startTimeMin, true);
                        timePickerDialog.setTitle("Select End Time");
                        timePickerDialog.show();
                    }
                };
                TimePickerDialog timePickerDialog = new TimePickerDialog(getContext(), onTimeSetListener, 0, 0, true);
                timePickerDialog.setTitle("Select Start Time");
                timePickerDialog.show();


            }
        });
    }


    public void tempBot(String value){
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                if(value == "initial"){
                    messages.add( new Message("Hi user, please select an option below to continue","",null, Message.TYPE_AI));
                    messages.add( new Message("Discover recommended places","",null, Message.TYPE_OPTIONS));
                    messages.add( new Message("Locate nearby attractions", "",null,Message.TYPE_OPTIONS));
                    messages.add( new Message("Learn about specific place","",null, Message.TYPE_OPTIONS));
                    messagesAdapter.notifyDataSetChanged();
                }
                if(value == "placeToVisit" && optionSelected == 1){
                    // Enable the keyboard after a delay
                    messages.add(new Message("Please type a city or country name","",null,Message.TYPE_AI));
                    messagesAdapter.notifyDataSetChanged();
                    binding.recyclerView.smoothScrollToPosition(messages.size() - 1); // This will automatically scroll to the end when a new message is added
                }
                if(value == "placeToVisit" && optionSelected == 3){
                    // Enable the keyboard after a delay
                    messages.add(new Message("Please type a question","",null,Message.TYPE_AI));
                    messagesAdapter.notifyDataSetChanged();
                    binding.recyclerView.smoothScrollToPosition(messages.size() - 1); // This will automatically scroll to the end when a new message is added
                }
                if(value == "AiTyping"){
                    messages.add(new Message("AI typing...","",null, Message.TYPE_AI));
                    messagesAdapter.notifyDataSetChanged();
                    binding.recyclerView.smoothScrollToPosition(messages.size() - 1); // This will automatically scroll to the end when a new message is added
                }
            }
        }, 500); // Delay of 3 seconds


    }


    public void hideKeyboard(View view) {
        InputMethodManager inputMethodManager = (InputMethodManager) requireActivity().getSystemService(Activity.INPUT_METHOD_SERVICE);
        inputMethodManager.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }



    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if(requestCode == LOCATION_PERMISSION_REQUEST_CODE){
            if(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED){
                //permission granted, you are good to go
            }else{
                Toast.makeText(getContext(), "Location access denied. Please turn on GPS to use this feature", Toast.LENGTH_SHORT).show();
            }
        }
    }

}