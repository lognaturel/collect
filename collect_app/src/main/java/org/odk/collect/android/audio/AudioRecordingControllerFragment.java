package org.odk.collect.android.audio;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import org.javarosa.core.model.instance.TreeReference;
import org.odk.collect.android.R;
import org.odk.collect.android.analytics.AnalyticsEvents;
import org.odk.collect.android.databinding.AudioRecordingControllerFragmentBinding;
import org.odk.collect.android.formentry.FormEntryViewModel;
import org.odk.collect.android.injection.DaggerUtils;
import org.odk.collect.audiorecorder.recording.AudioRecorder;
import org.odk.collect.audiorecorder.recording.RecordingSession;
import org.odk.collect.strings.format.LengthFormatterKt;

import javax.inject.Inject;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static org.odk.collect.android.utilities.DialogUtils.showIfNotShowing;

public class AudioRecordingControllerFragment extends Fragment {

    @Inject
    AudioRecorder audioRecorder;

    @Inject
    FormEntryViewModel.Factory formEntryViewModelFactory;

    public AudioRecordingControllerFragmentBinding binding;
    private FormEntryViewModel formEntryViewModel;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        DaggerUtils.getComponent(context).inject(this);

        formEntryViewModel = new ViewModelProvider(requireActivity(), formEntryViewModelFactory).get(FormEntryViewModel.class);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = AudioRecordingControllerFragmentBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }


    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        audioRecorder.getCurrentSession().observe(getViewLifecycleOwner(), session -> {
            if (session == null) {
                binding.getRoot().setVisibility(GONE);
            } else if (session.getFailedToStart() != null) {
                binding.getRoot().setVisibility(GONE);
                showIfNotShowing(AudioRecordingErrorDialogFragment.class, getParentFragmentManager());
            } else if (session.getFile() == null) {
                binding.getRoot().setVisibility(VISIBLE);

                binding.timeCode.setText(LengthFormatterKt.formatLength(session.getDuration()));
                binding.waveform.addAmplitude(session.getAmplitude());

                if (session.getPaused()) {
                    binding.pauseRecording.setIcon(ContextCompat.getDrawable(getContext(), R.drawable.ic_baseline_mic_24));
                    binding.pauseRecording.setContentDescription(getString(R.string.resume_recording));
                    binding.pauseRecording.setOnClickListener(v -> audioRecorder.resume());

                    binding.recordingIcon.setImageDrawable(ContextCompat.getDrawable(getContext(), R.drawable.ic_pause_24dp));
                } else {
                    binding.pauseRecording.setIcon(ContextCompat.getDrawable(getContext(), R.drawable.ic_pause_24dp));
                    binding.pauseRecording.setContentDescription(getString(R.string.pause_recording));
                    binding.pauseRecording.setOnClickListener(v -> {
                        audioRecorder.pause();
                        formEntryViewModel.logFormEvent(AnalyticsEvents.AUDIO_RECORDING_PAUSE);
                    });

                    binding.recordingIcon.setImageDrawable(ContextCompat.getDrawable(getContext(), R.drawable.ic_baseline_mic_24));
                }

                // Pause not available before API 24
                if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.N) {
                    binding.pauseRecording.setVisibility(GONE);
                }

                if (isBackgroundRecording(session)) {
                    binding.pauseRecording.setVisibility(GONE);
                    binding.stopRecording.setVisibility(GONE);
                }
            } else {
                binding.getRoot().setVisibility(GONE);
            }
        });

        binding.stopRecording.setOnClickListener(v -> audioRecorder.stop());
    }

    private boolean isBackgroundRecording(RecordingSession session) {
        return session.getId() instanceof TreeReference;
    }
}
