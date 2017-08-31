/***
 * Copyright (c) 2015-2016 CommonsWare, LLC
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you
 * may
 * not use this file except in compliance with the License. You may
 * obtain
 * a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing,
 * software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.
 * See the License for the specific language governing permissions
 * and
 * limitations under the License.
 */

package com.commonsware.cwac.cam2;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.ActionBar;
import android.app.Fragment;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.media.MediaScannerConnection;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.constraint.ConstraintLayout;
import android.support.constraint.ConstraintSet;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.Chronometer;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;

import com.github.clans.fab.FloatingActionButton;
import com.github.clans.fab.FloatingActionMenu;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;

import rx.Single;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.schedulers.Schedulers;

/**
 * Fragment for displaying a camera preview, with hooks to allow
 * you (or the user) to take a picture.
 */
public class CameraFragment extends Fragment
  implements ReverseChronometer.Listener {
  private static final String ARG_OUTPUT="output";
  private static final String ARG_UPDATE_MEDIA_STORE=
    "updateMediaStore";
  private static final String ARG_SKIP_ORIENTATION_NORMALIZATION
    ="skipOrientationNormalization";
  private static final String ARG_IS_VIDEO="isVideo";
  private static final String ARG_QUALITY="quality";
  private static final String ARG_SIZE_LIMIT="sizeLimit";
  private static final String ARG_DURATION_LIMIT="durationLimit";
  private static final String ARG_ZOOM_STYLE="zoomStyle";
  private static final String ARG_FACING_EXACT_MATCH=
    "facingExactMatch";
  private static final String ARG_CHRONOTYPE="chronotype";
  private static final String ARG_RULE_OF_THIRDS="ruleOfThirds";
  private static final String ARG_TIMER_DURATION="timerDuration";
  private static final int PINCH_ZOOM_DELTA=20;
  private static final String ARG_WIDTH_HEIGHT = "width_height";
  private static final String ARG_COMPRESSION_PERCENTAGE = "compression_percent";
  private static final String ARG_MULTIPLE_PHOTOS = "multiple_photos";
  private static final java.lang.String STATE_FLASH_MODE_ORDINAL = "flash_mode_ordinal";
  private CameraController ctlr;
  private ViewGroup previewStack;
  private FloatingActionButton fabPicture;
  private FloatingActionButton fabSwitch;
  private View progress;
  private boolean isVideoRecording=false;
  private boolean mirrorPreview=false;
  private ScaleGestureDetector scaleDetector;
  private boolean inSmoothPinchZoom=false;
  private SeekBar zoomSlider;
  private Chronometer chronometer;
  private ReverseChronometer reverseChronometer;
  private View blackout;
  private ImageView freeze;
  private Toolbar toolbar;
  private FlashMode flashMode;
  private MenuItem flashMenuItem;
  private ImageView shutter;
  private ImageButton gallery;
  private Subscription photoTakenSubscription;
  private ConstraintLayout root;

  public static CameraFragment newPictureInstance(Uri output,
                                                  boolean updateMediaStore,
                                                  int quality,
                                                  ZoomStyle zoomStyle,
                                                  boolean facingExactMatch,
                                                  boolean skipOrientationNormalization,
                                                  int timerDuration,
                                                  boolean ruleOfThirds) {
    CameraFragment f=new CameraFragment();
    Bundle args=new Bundle();

    args.putParcelable(ARG_OUTPUT, output);
    args.putBoolean(ARG_UPDATE_MEDIA_STORE, updateMediaStore);
    args.putBoolean(ARG_SKIP_ORIENTATION_NORMALIZATION,
      skipOrientationNormalization);
    args.putInt(ARG_QUALITY, quality);
    args.putBoolean(ARG_IS_VIDEO, false);
    args.putSerializable(ARG_ZOOM_STYLE, zoomStyle);
    args.putBoolean(ARG_FACING_EXACT_MATCH, facingExactMatch);
    args.putInt(ARG_TIMER_DURATION, timerDuration);
    args.putBoolean(ARG_RULE_OF_THIRDS, ruleOfThirds);
    f.setArguments(args);

    return (f);
  }

  public static CameraFragment newPictureInstance(Uri output,
                                                  boolean updateMediaStore,
                                                  int quality,
                                                  ZoomStyle zoomStyle,
                                                  boolean facingExactMatch,
                                                  boolean skipOrientationNormalization,
                                                  int timerDuration,
                                                  boolean ruleOfThirds,
                                                  int widthHeight,
                                                  int compressionPercentage,
                                                  boolean multiplePhotos) {
    CameraFragment f=new CameraFragment();
    Bundle args=new Bundle();

    args.putParcelable(ARG_OUTPUT, output);
    args.putBoolean(ARG_UPDATE_MEDIA_STORE, updateMediaStore);
    args.putBoolean(ARG_SKIP_ORIENTATION_NORMALIZATION,
            skipOrientationNormalization);
    args.putInt(ARG_QUALITY, quality);
    args.putBoolean(ARG_IS_VIDEO, false);
    args.putSerializable(ARG_ZOOM_STYLE, zoomStyle);
    args.putBoolean(ARG_FACING_EXACT_MATCH, facingExactMatch);
    args.putInt(ARG_TIMER_DURATION, timerDuration);
    args.putBoolean(ARG_RULE_OF_THIRDS, ruleOfThirds);
    args.putInt(ARG_WIDTH_HEIGHT, widthHeight);
    args.putInt(ARG_COMPRESSION_PERCENTAGE, compressionPercentage);
    args.putBoolean(ARG_MULTIPLE_PHOTOS, multiplePhotos);
    f.setArguments(args);

    return (f);
  }

  public static CameraFragment newVideoInstance(Uri output,
                                                boolean updateMediaStore,
                                                int quality,
                                                int sizeLimit,
                                                int durationLimit,
                                                ZoomStyle zoomStyle,
                                                boolean facingExactMatch,
                                                ChronoType chronoType,
                                                boolean ruleOfThirds) {
    CameraFragment f=new CameraFragment();
    Bundle args=new Bundle();

    args.putParcelable(ARG_OUTPUT, output);
    args.putBoolean(ARG_UPDATE_MEDIA_STORE, updateMediaStore);
    args.putBoolean(ARG_IS_VIDEO, true);
    args.putInt(ARG_QUALITY, quality);
    args.putInt(ARG_SIZE_LIMIT, sizeLimit);
    args.putInt(ARG_DURATION_LIMIT, durationLimit);
    args.putSerializable(ARG_ZOOM_STYLE, zoomStyle);
    args.putBoolean(ARG_FACING_EXACT_MATCH, facingExactMatch);
    args.putBoolean(ARG_RULE_OF_THIRDS, ruleOfThirds);

    if (durationLimit>0 || chronoType!=ChronoType.COUNT_DOWN) {
      args.putSerializable(ARG_CHRONOTYPE, chronoType);
    }

    f.setArguments(args);

    return (f);
  }

  /**
   * Standard fragment entry point.
   *
   * @param savedInstanceState State of a previous instance
   */
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    setRetainInstance(true);
    scaleDetector=
      new ScaleGestureDetector(getActivity().getApplicationContext(),
        scaleListener);
  }

  /**
   * Standard lifecycle method, passed along to the CameraController.
   */
  @Override
  public void onStart() {
    super.onStart();

    AbstractCameraActivity.BUS.register(this);

    if (ctlr!=null) {
      ctlr.start();
    }
  }

  @Override
  public void onHiddenChanged(boolean isHidden) {
    super.onHiddenChanged(isHidden);

    if (!isHidden) {
      ActionBar ab=getActivity().getActionBar();

      if (ab!=null) {
        ab.setBackgroundDrawable(getActivity()
          .getResources()
          .getDrawable(
            R.drawable.cwac_cam2_action_bar_bg_transparent));
        ab.setTitle("");

        if (Build.VERSION.SDK_INT>=Build.VERSION_CODES.LOLLIPOP) {
          ab.setDisplayHomeAsUpEnabled(false);
        }
        else {
          ab.setDisplayShowHomeEnabled(false);
          ab.setHomeButtonEnabled(false);
        }
      }

      if (fabPicture!=null) {
        shutter.setEnabled(true);
        fabPicture.setEnabled(true);
        fabSwitch.setEnabled(canSwitchSources());
      }
    }
  }

  /**
   * Standard lifecycle method, for when the fragment moves into
   * the stopped state. Passed along to the CameraController.
   */
  @Override
  public void onStop() {
    stopChronometers();

    if (ctlr!=null) {
      try {
        ctlr.stop();
      }
      catch (Exception e) {
        ctlr.postError(ErrorConstants.ERROR_STOPPING, e);
        Log.e(getClass().getSimpleName(),
          "Exception stopping controller", e);
      }
    }

    AbstractCameraActivity.BUS.unregister(this);

    super.onStop();
  }

  /**
   * Standard lifecycle method, for when the fragment is utterly,
   * ruthlessly destroyed. Passed along to the CameraController,
   * because why should the fragment have all the fun?
   */
  @Override
  public void onDestroy() {
    if (ctlr!=null) {
      ctlr.destroy();
    }

    super.onDestroy();
    if (photoTakenSubscription != null) {
      photoTakenSubscription.unsubscribe();
    }
  }

  /**
   * Standard callback method to create the UI managed by
   * this fragment.
   *
   * @param inflater Used to inflate layouts
   * @param container Parent of the fragment's UI (eventually)
   * @param savedInstanceState State of a previous instance
   * @return the UI being managed by this fragment
   */
  @Override
  public View onCreateView(LayoutInflater inflater,
                           ViewGroup container,
                           Bundle savedInstanceState) {
    final View v=
      inflater.inflate(R.layout.cwac_cam2_fragment, container, false);

    if (getResources().getBoolean(R.bool.sw600dp)) {
      if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N || !getActivity().isInMultiWindowMode()) {
        final int navBarHeight = getNavigationBarHeight();
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
          ((ViewGroup.MarginLayoutParams) v.getLayoutParams()).setMargins(0, navBarHeight, 0, navBarHeight);
        } else {
          ((ViewGroup.MarginLayoutParams) v.getLayoutParams()).setMargins(navBarHeight, 0, navBarHeight, 0);
        }
      }
    }
    previewStack=
      (ViewGroup)v.findViewById(R.id.cwac_cam2_preview_stack);

    blackout = v.findViewById(R.id.cwac_cam2_preview_blackout);
    freeze = (ImageView) v.findViewById(R.id.cwac_cam2_preview_freeze);
    progress=v.findViewById(R.id.cwac_cam2_progress);
    gallery = (ImageButton) v.findViewById(R.id.cwac_cam2_gallery);
    root = (ConstraintLayout) v.findViewById(R.id.cwac_cam2_root);
    v.findViewById(R.id.cwac_cam2_switch).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        try {
          ctlr.switchCamera();
        }
        catch (Exception e) {
          ctlr.postError(ErrorConstants.ERROR_SWITCHING_CAMERAS, e);
          Log.e(getClass().getSimpleName(),
                  "Exception switching camera", e);
        }
      }
    });
    shutter=(ImageView)v.findViewById(R.id.cwac_cam2_shutter);
    shutter.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        performCameraAction();
      }
    });
    toolbar=(Toolbar)v.findViewById(R.id.cwac_cam2_toolbar);
    toolbar.setTitle("Lounge Room");
    toolbar.setNavigationIcon(R.drawable.ic_arrow_back_white_24dp);
    toolbar.inflateMenu(R.menu.camera_menu);
    toolbar.setNavigationOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        getActivity().finish();
      }
    });
    if (savedInstanceState != null) {
      final int stateFlashModeOrdinal = savedInstanceState.getInt(STATE_FLASH_MODE_ORDINAL, -1);
      if (stateFlashModeOrdinal == -1) {
        flashMode = null;
      } else {
        flashMode = FlashMode.values()[stateFlashModeOrdinal];
      }
    }
    flashMenuItem = toolbar.getMenu().findItem(R.id.flash_mode);
    toolbar.setOnMenuItemClickListener(new Toolbar.OnMenuItemClickListener() {
      @Override
      public boolean onMenuItemClick(MenuItem item) {
        if (item == flashMenuItem) {
          if (ctlr == null) {
            // we don't have a controller, do nothing
            return false;
          }
          final List<FlashMode> availableFlashModes = ctlr.getAvailableFlashModes();
          FlashMode newFlashMode;
          if (flashMode == null) {
            // if we don't have one, presume we are using the first one
            newFlashMode = availableFlashModes.get(0);
          } else {
            int index = availableFlashModes.indexOf(flashMode);
            newFlashMode = availableFlashModes.get((index + 1) % availableFlashModes.size());
          }
          if (newFlashMode == flashMode) {
            // no change just return
            return false;
          }
          try {
            ctlr.stop();
            getController().getEngine().setPreferredFlashModes(Collections.singletonList(newFlashMode));
            getController().start();
            ctlr.start();
            flashMode = newFlashMode;
            updateFlashIcon();
            return true;
          } catch (Exception e) {
            ctlr.postError(ErrorConstants.ERROR_SWITCHING_FLASHMODE, e);
            Log.e(getClass().getSimpleName(),
                    "Exception switching flash mode", e);
            return false;
          }
        }

        // success! change the icon

        return false;
      }
    });
    fabPicture=
      (FloatingActionButton)v.findViewById(R.id.cwac_cam2_picture);
    reverseChronometer=
      (ReverseChronometer)v.findViewById(R.id.rchrono);

    if (isVideo()) {
      fabPicture.setImageResource(R.drawable.cwac_cam2_ic_videocam);
      chronometer=(Chronometer)v.findViewById(R.id.chrono);
    }

    fabPicture.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        performCameraAction();
      }
    });

    fabSwitch=(FloatingActionButton)v.findViewById(
      R.id.cwac_cam2_switch_camera);
    fabSwitch.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        progress.setVisibility(View.VISIBLE);
        fabSwitch.setEnabled(false);

        try {
          ctlr.switchCamera();
        }
        catch (Exception e) {
          ctlr.postError(ErrorConstants.ERROR_SWITCHING_CAMERAS, e);
          Log.e(getClass().getSimpleName(),
            "Exception switching camera", e);
        }
      }
    });

    changeMenuIconAnimation(
      (FloatingActionMenu)v.findViewById(R.id.cwac_cam2_settings));

    onHiddenChanged(false); // hack, since this does not get
    // called on initial display

    fabPicture.setEnabled(false);
    fabSwitch.setEnabled(false);

    if (ctlr!=null && ctlr.getNumberOfCameras()>0) {
      prepController();
    }

    if (showRuleOfThirds()) {
      v.findViewById(R.id.rule_of_thirds).setVisibility(View.VISIBLE);
    }

    updateConstraints(getResources().getConfiguration());
    return(v);
  }

  @Override
  public void onConfigurationChanged(Configuration newConfig) {
    super.onConfigurationChanged(newConfig);
    updateConstraints(newConfig);
  }


  private void updateConstraints(Configuration config) {
    ConstraintSet set = new ConstraintSet();
    set.clone(root);
    if (config.smallestScreenWidthDp >= 600) {
      // tablet
      if (config.orientation == Configuration.ORIENTATION_LANDSCAPE) {
        // toolbar
        showToolbarAsOverlay(set);
        // zoom
        showZoomAsOverlay(set);
        // preview stack
        set.connect(R.id.cwac_cam2_preview_stack, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP);
        set.connect(R.id.cwac_cam2_preview_stack, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM);
        set.connect(R.id.cwac_cam2_preview_stack, ConstraintSet.START, R.id.preview_stack_start, ConstraintSet.START);
        set.connect(R.id.cwac_cam2_preview_stack, ConstraintSet.END, R.id.preview_stack_end, ConstraintSet.END);
        // button bar
        set.setVisibility(R.id.button_bar_top, View.GONE);
        set.setVisibility(R.id.button_bar_bottom, View.GONE);
        set.setVisibility(R.id.button_bar_start, View.VISIBLE);
        set.setVisibility(R.id.button_bar_end, View.VISIBLE);
        set.connect(R.id.cwac_cam2_switch, ConstraintSet.TOP, R.id.cwac_cam2_shutter, ConstraintSet.BOTTOM);
        set.connect(R.id.cwac_cam2_switch, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM);
        set.connect(R.id.cwac_cam2_switch, ConstraintSet.START, R.id.button_bar_end, ConstraintSet.START);
        set.connect(R.id.cwac_cam2_switch, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END);
        set.connect(R.id.cwac_cam2_shutter, ConstraintSet.TOP, R.id.cwac_cam2_gallery, ConstraintSet.BOTTOM);
        set.connect(R.id.cwac_cam2_shutter, ConstraintSet.BOTTOM, R.id.cwac_cam2_switch, ConstraintSet.TOP);
        set.connect(R.id.cwac_cam2_shutter, ConstraintSet.START, R.id.button_bar_end, ConstraintSet.START);
        set.connect(R.id.cwac_cam2_shutter, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END);
        set.connect(R.id.cwac_cam2_gallery, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP);
        set.connect(R.id.cwac_cam2_gallery, ConstraintSet.BOTTOM, R.id.cwac_cam2_shutter, ConstraintSet.TOP);
        set.connect(R.id.cwac_cam2_gallery, ConstraintSet.START, R.id.button_bar_end, ConstraintSet.START);
        set.connect(R.id.cwac_cam2_gallery, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END);
      } else {
        // toolbar
        showToolbarAsOverlay(set);
        // zoom
        showZoomAsOverlay(set);
        // preview stack
        set.connect(R.id.cwac_cam2_preview_stack, ConstraintSet.TOP, R.id.preview_stack_top, ConstraintSet.TOP);
        set.connect(R.id.cwac_cam2_preview_stack, ConstraintSet.BOTTOM, R.id.preview_stack_bottom, ConstraintSet.BOTTOM);
        set.connect(R.id.cwac_cam2_preview_stack, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START);
        set.connect(R.id.cwac_cam2_preview_stack, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END);
        // button bar
        set.setVisibility(R.id.button_bar_top, View.VISIBLE);
        set.setVisibility(R.id.button_bar_bottom, View.VISIBLE);
        set.setVisibility(R.id.button_bar_start, View.GONE);
        set.setVisibility(R.id.button_bar_end, View.GONE);
        set.connect(R.id.cwac_cam2_switch, ConstraintSet.TOP, R.id.button_bar_bottom, ConstraintSet.TOP);
        set.connect(R.id.cwac_cam2_switch, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM);
        set.connect(R.id.cwac_cam2_switch, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START);
        set.connect(R.id.cwac_cam2_switch, ConstraintSet.END, R.id.cwac_cam2_shutter, ConstraintSet.START);
        set.connect(R.id.cwac_cam2_shutter, ConstraintSet.TOP, R.id.button_bar_bottom, ConstraintSet.TOP);
        set.connect(R.id.cwac_cam2_shutter, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM);
        set.connect(R.id.cwac_cam2_shutter, ConstraintSet.START, R.id.cwac_cam2_switch, ConstraintSet.END);
        set.connect(R.id.cwac_cam2_shutter, ConstraintSet.END, R.id.cwac_cam2_gallery, ConstraintSet.START);
        set.connect(R.id.cwac_cam2_gallery, ConstraintSet.TOP, R.id.button_bar_bottom, ConstraintSet.TOP);
        set.connect(R.id.cwac_cam2_gallery, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM);
        set.connect(R.id.cwac_cam2_gallery, ConstraintSet.START, R.id.cwac_cam2_shutter, ConstraintSet.END);
        set.connect(R.id.cwac_cam2_gallery, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END);
      }
    } else {
      if (config.orientation == Configuration.ORIENTATION_LANDSCAPE) {
        // toolbar
        showToolbarAsOverlay(set);
        // zoom
        showZoomAsOverlay(set);
        // preview stack
        set.connect(R.id.cwac_cam2_preview_stack, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP);
        set.connect(R.id.cwac_cam2_preview_stack, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM);
        set.connect(R.id.cwac_cam2_preview_stack, ConstraintSet.START, R.id.preview_stack_start, ConstraintSet.START);
        set.connect(R.id.cwac_cam2_preview_stack, ConstraintSet.END, R.id.preview_stack_end, ConstraintSet.END);
        // button bar
        set.setVisibility(R.id.button_bar_top, View.GONE);
        set.setVisibility(R.id.button_bar_bottom, View.GONE);
        set.setVisibility(R.id.button_bar_start, View.VISIBLE);
        set.setVisibility(R.id.button_bar_end, View.VISIBLE);
        set.connect(R.id.cwac_cam2_switch, ConstraintSet.TOP, R.id.cwac_cam2_shutter, ConstraintSet.BOTTOM);
        set.connect(R.id.cwac_cam2_switch, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM);
        set.connect(R.id.cwac_cam2_switch, ConstraintSet.START, R.id.button_bar_end, ConstraintSet.START);
        set.connect(R.id.cwac_cam2_switch, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END);
        set.connect(R.id.cwac_cam2_shutter, ConstraintSet.TOP, R.id.cwac_cam2_gallery, ConstraintSet.BOTTOM);
        set.connect(R.id.cwac_cam2_shutter, ConstraintSet.BOTTOM, R.id.cwac_cam2_switch, ConstraintSet.TOP);
        set.connect(R.id.cwac_cam2_shutter, ConstraintSet.START, R.id.button_bar_end, ConstraintSet.START);
        set.connect(R.id.cwac_cam2_shutter, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END);
        set.connect(R.id.cwac_cam2_gallery, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP);
        set.connect(R.id.cwac_cam2_gallery, ConstraintSet.BOTTOM, R.id.cwac_cam2_shutter, ConstraintSet.TOP);
        set.connect(R.id.cwac_cam2_gallery, ConstraintSet.START, R.id.button_bar_end, ConstraintSet.START);
        set.connect(R.id.cwac_cam2_gallery, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END);
      } else {
        // toolbar
        set.connect(R.id.cwac_cam2_toolbar, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP);
        set.connect(R.id.cwac_cam2_toolbar, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START);
        set.connect(R.id.cwac_cam2_toolbar, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END);
        set.setVisibility(R.id.cwac_cam2_toolbar_overlay, View.GONE);
        // zoom
        set.connect(R.id.cwac_cam2_zoom_minus, ConstraintSet.TOP, R.id.cwac_cam2_preview_stack, ConstraintSet.BOTTOM);
        set.clear(R.id.cwac_cam2_zoom_minus, ConstraintSet.BOTTOM);
        set.connect(R.id.cwac_cam2_zoom_minus, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START);
        set.clear(R.id.cwac_cam2_zoom_minus, ConstraintSet.END);
        set.connect(R.id.cwac_cam2_zoom_plus, ConstraintSet.TOP, R.id.cwac_cam2_preview_stack, ConstraintSet.BOTTOM);
        set.clear(R.id.cwac_cam2_zoom_plus, ConstraintSet.BOTTOM);
        set.clear(R.id.cwac_cam2_zoom_plus, ConstraintSet.START);
        set.connect(R.id.cwac_cam2_zoom_plus, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END);
        set.clear(R.id.cwac_cam2_preview_stack, ConstraintSet.BOTTOM);
        set.setVisibility(R.id.cwac_cam2_zoom_overlay, View.GONE);
        // preview stack
        set.connect(R.id.cwac_cam2_preview_stack, ConstraintSet.TOP, R.id.cwac_cam2_toolbar, ConstraintSet.BOTTOM);
        set.clear(R.id.cwac_cam2_preview_stack, ConstraintSet.BOTTOM);
        set.connect(R.id.cwac_cam2_preview_stack, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START);
        set.connect(R.id.cwac_cam2_preview_stack, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END);
        // button bar
        set.setVisibility(R.id.button_bar_top, View.GONE);
        set.setVisibility(R.id.button_bar_bottom, View.GONE);
        set.setVisibility(R.id.button_bar_start, View.GONE);
        set.setVisibility(R.id.button_bar_end, View.GONE);
        set.connect(R.id.cwac_cam2_switch, ConstraintSet.TOP, R.id.cwac_cam2_shutter, ConstraintSet.TOP);
        set.connect(R.id.cwac_cam2_switch, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM);
        set.connect(R.id.cwac_cam2_switch, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START);
        set.connect(R.id.cwac_cam2_switch, ConstraintSet.END, R.id.cwac_cam2_shutter, ConstraintSet.START);
        set.clear(R.id.cwac_cam2_shutter, ConstraintSet.TOP);
        set.connect(R.id.cwac_cam2_shutter, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM);
        set.connect(R.id.cwac_cam2_shutter, ConstraintSet.START, R.id.cwac_cam2_switch, ConstraintSet.END);
        set.connect(R.id.cwac_cam2_shutter, ConstraintSet.END, R.id.cwac_cam2_gallery, ConstraintSet.START);
        set.connect(R.id.cwac_cam2_gallery, ConstraintSet.TOP, R.id.cwac_cam2_shutter, ConstraintSet.TOP);
        set.connect(R.id.cwac_cam2_gallery, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM);
        set.connect(R.id.cwac_cam2_gallery, ConstraintSet.START, R.id.cwac_cam2_shutter, ConstraintSet.END);
        set.connect(R.id.cwac_cam2_gallery, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END);
      }
    }
    set.applyTo(root);
  }

  private void showZoomAsOverlay(ConstraintSet set) {
    set.clear(R.id.cwac_cam2_zoom_minus, ConstraintSet.TOP);
    set.connect(R.id.cwac_cam2_zoom_minus, ConstraintSet.BOTTOM, R.id.cwac_cam2_preview_stack, ConstraintSet.BOTTOM);
    set.connect(R.id.cwac_cam2_zoom_minus, ConstraintSet.START, R.id.cwac_cam2_preview_stack, ConstraintSet.START);
    set.clear(R.id.cwac_cam2_zoom_minus, ConstraintSet.END);
    set.clear(R.id.cwac_cam2_zoom_plus, ConstraintSet.TOP);
    set.connect(R.id.cwac_cam2_zoom_plus, ConstraintSet.BOTTOM, R.id.cwac_cam2_preview_stack, ConstraintSet.BOTTOM);
    set.clear(R.id.cwac_cam2_zoom_plus, ConstraintSet.START);
    set.connect(R.id.cwac_cam2_zoom_plus, ConstraintSet.END, R.id.cwac_cam2_preview_stack, ConstraintSet.END);
    set.setVisibility(R.id.cwac_cam2_zoom_overlay, View.VISIBLE);
  }

  private void showToolbarAsOverlay(ConstraintSet set) {
    set.connect(R.id.cwac_cam2_toolbar, ConstraintSet.TOP, R.id.cwac_cam2_preview_stack, ConstraintSet.TOP);
    set.connect(R.id.cwac_cam2_toolbar, ConstraintSet.START, R.id.cwac_cam2_preview_stack, ConstraintSet.START);
    set.connect(R.id.cwac_cam2_toolbar, ConstraintSet.END, R.id.cwac_cam2_preview_stack, ConstraintSet.END);
    set.setVisibility(R.id.cwac_cam2_toolbar_overlay, View.VISIBLE);
  }

  private void updateFlashIcon() {
    if (flashMode == null) {
      flashMenuItem.setVisible(false);
      flashMenuItem.setEnabled(false);
    } else {
      flashMenuItem.setVisible(true);
      flashMenuItem.setEnabled(true);
    }
    switch(flashMode) {
      case ALWAYS:
        flashMenuItem.setIcon(R.drawable.ic_flash_on_white_24dp);
        break;
      case AUTO:
        flashMenuItem.setIcon(R.drawable.ic_flash_auto_white_24dp);
        break;
      case OFF:
        flashMenuItem.setIcon(R.drawable.ic_flash_off_white_24dp);
        break;
      case REDEYE:
        flashMenuItem.setIcon(R.drawable.ic_remove_red_eye_white_24dp);
        break;
      case TORCH:
        flashMenuItem.setIcon(R.drawable.ic_highlight_white_24dp);
        break;
    }
  }


  private int getNavigationBarHeight() {
    final Resources resources = getResources();
    int id = resources.getIdentifier(resources.getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT ? "navigation_bar_height" : "navigation_bar_height_landscape","dimen", "android");
    if (id > 0) {
      return resources.getDimensionPixelSize(id);
    }
    return 0;
  }

  @Override
  public void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    if (flashMode != null) {
      outState.putInt(STATE_FLASH_MODE_ORDINAL, flashMode.ordinal());
    }
  }

  @Override
  public void onCountdownCompleted() {
    takePicture();
  }

  public void shutdown() {
    if (isVideoRecording) {
      stopVideoRecording(true);
    }
    else {
      progress.setVisibility(View.VISIBLE);

      if (ctlr!=null) {
        try {
          ctlr.stop();
        }
        catch (Exception e) {
          ctlr.postError(ErrorConstants.ERROR_STOPPING, e);
          Log.e(getClass().getSimpleName(),
            "Exception stopping controller", e);
        }
      }
    }
  }

  /**
   * @return the CameraController this fragment delegates to
   */
  public CameraController getController() {
    return (ctlr);
  }

  /**
   * Establishes the controller that this fragment delegates to
   *
   * @param ctlr the controller that this fragment delegates to
   */
  public void setController(CameraController ctlr) {
    int currentCamera=-1;

    if (this.ctlr!=null) {
      currentCamera=this.ctlr.getCurrentCamera();
    }

    this.ctlr=ctlr;
    ctlr.setQuality(getArguments().getInt(ARG_QUALITY, 1));

    if (currentCamera>-1) {
      ctlr.setCurrentCamera(currentCamera);
    }
  }

  /**
   * Indicates if we should mirror the preview or not. Defaults
   * to false.
   *
   * @param mirror true if we should horizontally mirror the
   *               preview, false otherwise
   */
  public void setMirrorPreview(boolean mirror) {
    this.mirrorPreview=mirror;
  }

  @SuppressWarnings("unused")
  @Subscribe(threadMode=ThreadMode.MAIN)
  public void onEventMainThread(
    CameraController.ControllerReadyEvent event) {
    if (event.isEventForController(ctlr)) {
      prepController();
    }
  }

  @SuppressWarnings("unused")
  @Subscribe(threadMode=ThreadMode.MAIN)
  public void onEventMainThread(CameraEngine.OpenedEvent event) {
    if (event.exception==null) {
      progress.setVisibility(View.GONE);
      fabSwitch.setEnabled(canSwitchSources());
      fabPicture.setEnabled(true);
      shutter.setEnabled(true);
      zoomSlider=(SeekBar)getView().findViewById(R.id.cwac_cam2_zoom);

      int timerDuration=getArguments().getInt(ARG_TIMER_DURATION);

      if (timerDuration>0) {
        reverseChronometer.setVisibility(View.VISIBLE);
        reverseChronometer.setOverallDuration(timerDuration);
        reverseChronometer.setListener(this);
        reverseChronometer.reset();
        reverseChronometer.run();
      }

      if (ctlr.supportsZoom()) {
        if (getZoomStyle()==ZoomStyle.PINCH) {
          previewStack.setOnTouchListener(
            new View.OnTouchListener() {
              @Override
              public boolean onTouch(View v, MotionEvent event) {
                return (scaleDetector.onTouchEvent(event));
              }
            });
        }
        else if (getZoomStyle()==ZoomStyle.SEEKBAR) {
          zoomSlider.setVisibility(View.VISIBLE);
          zoomSlider.setOnSeekBarChangeListener(seekListener);
        }
      }
      else {
        previewStack.setOnTouchListener(null);
        zoomSlider.setVisibility(View.GONE);
      }
      flashMode = ctlr.getCurrentFlashMode();
      updateFlashIcon();
    }
    else {
      ctlr.postError(ErrorConstants.ERROR_OPEN_CAMERA,
        event.exception);
      getActivity().finish();
    }
  }

  @SuppressWarnings("unused")
  @Subscribe(threadMode=ThreadMode.MAIN)
  public void onEventMainThread(CameraEngine.VideoTakenEvent event) {
    isVideoRecording=false;
    stopChronometers();

    if (event.exception==null) {
      if (getArguments().getBoolean(ARG_UPDATE_MEDIA_STORE, false)) {
        final Context app=getActivity().getApplicationContext();
        Uri output=getArguments().getParcelable(ARG_OUTPUT);
        final String path=output.getPath();

        new Thread() {
          @Override
          public void run() {
            SystemClock.sleep(2000);
            MediaScannerConnection.scanFile(app,
              new String[]{path}, new String[]{"video/mp4"},
              null);
          }
        }.start();
      }

      isVideoRecording=false;
      setVideoFABToNormal();
    }
    else if (getActivity().isFinishing()) {
      shutdown();
    }
    else {
      ctlr.postError(ErrorConstants.ERROR_VIDEO_TAKEN,
        event.exception);
      getActivity().finish();
    }
  }

  @SuppressWarnings("unused")
  @Subscribe(threadMode=ThreadMode.MAIN)
  public void onEventMainThread(
    CameraEngine.SmoothZoomCompletedEvent event) {
    inSmoothPinchZoom=false;
    zoomSlider.setEnabled(true);
  }

  protected void performCameraAction() {
    if (isVideo()) {
      recordVideo();
    }
    else {
      takePicture();
    }
  }

  public void makeReady() {
    fabPicture.setEnabled(true);
    shutter.setEnabled(true);
    fabSwitch.setEnabled(canSwitchSources());
  }

  @SuppressWarnings("unused")
  @Subscribe(threadMode =ThreadMode.MAIN)
  public void onEventMainThread(CameraEngine.ShutterEvent event) {
    blackout.animate().alpha(1).setDuration(100).setInterpolator(new DecelerateInterpolator()).withEndAction(new Runnable() {
      @Override
      public void run() {
        blackout.animate().alpha(0).setDuration(100).setInterpolator(new AccelerateInterpolator()).start();
      }
    }).start();  }

  @SuppressWarnings("unused")
  @Subscribe(threadMode =ThreadMode.MAIN)
  public void onEventMainThread(final CameraEngine.PictureTakenEvent event) {
    final int size = getResources().getDimensionPixelSize(R.dimen.cwac_cam2_button_size);
    photoTakenSubscription = Single.fromCallable(new Callable<Bitmap>() {
      @Override
      public Bitmap call() throws Exception {
        final Bitmap bm = event.getImageContext().buildPreviewThumbnail(getActivity(), 0.1f, true);
        return ThumbnailUtils.extractThumbnail(bm, size, size, ThumbnailUtils.OPTIONS_RECYCLE_INPUT);
      }
    }).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe(new Action1<Bitmap>() {
      @Override
      public void call(final Bitmap bitmap) {
        freeze.setImageBitmap(bitmap);
        freeze.setScaleX(1);
        freeze.setScaleY(1);
        freeze.setPivotX(gallery.getX() + gallery.getWidth() / 2 - freeze.getX());
        freeze.setPivotY(gallery.getY() + gallery.getHeight() / 2 - freeze.getY());
        freeze.animate().scaleY(0).scaleX(0).setDuration(200).setInterpolator(new AccelerateInterpolator()).withEndAction(new Runnable() {
          @Override
          public void run() {
            freeze.setImageBitmap(null);
            gallery.setImageBitmap(bitmap);
          }
        }).start();
      }
    });
  }


  private void takePicture() {
    Uri output=getArguments().getParcelable(ARG_OUTPUT);

    PictureTransaction.Builder b=new PictureTransaction.Builder();

    if (output!=null) {
      if (getArguments().getBoolean(ARG_MULTIPLE_PHOTOS, false)) {
        output = output.buildUpon().appendPath(UUID.randomUUID().toString()).build();
      }
      b.toUri(getActivity(), output,
        getArguments().getBoolean(ARG_UPDATE_MEDIA_STORE, false),
        getArguments().getBoolean(ARG_SKIP_ORIENTATION_NORMALIZATION,
          false),
        getArguments().getInt(ARG_WIDTH_HEIGHT, -1),
        getArguments().getInt(ARG_COMPRESSION_PERCENTAGE, -1));
    }

    fabPicture.setEnabled(false);
    fabSwitch.setEnabled(false);
    shutter.setEnabled(false);
    ctlr.takePicture(b.build());
  }

  private void recordVideo() {
    if (isVideoRecording) {
      stopVideoRecording(false);
    }
    else {
      try {
        VideoTransaction.Builder b=
          new VideoTransaction.Builder();
        Uri output=getArguments().getParcelable(ARG_OUTPUT);

        b.to(new File(output.getPath()))
          .quality(getArguments().getInt(ARG_QUALITY, 1))
          .sizeLimit(getArguments().getInt(ARG_SIZE_LIMIT, 0))
          .durationLimit(
            getArguments().getInt(ARG_DURATION_LIMIT, 0));

        ctlr.recordVideo(b.build());
        isVideoRecording=true;
        fabPicture.setImageResource(
          R.drawable.cwac_cam2_ic_stop);
        fabPicture.setColorNormalResId(
          R.color.cwac_cam2_recording_fab);
        fabPicture.setColorPressedResId(
          R.color.cwac_cam2_recording_fab_pressed);
        fabSwitch.setEnabled(false);
        configureChronometer();
      }
      catch (Exception e) {
        Log.e(getClass().getSimpleName(),
          "Exception recording video", e);
        // TODO: um, do something here
      }
    }
  }

  public void stopVideoRecording() {
    stopVideoRecording(true);
  }

  private void stopVideoRecording(boolean abandon) {
    setVideoFABToNormal();

    try {
      ctlr.stopVideoRecording(abandon);
    }
    catch (Exception e) {
      ctlr.postError(ErrorConstants.ERROR_STOPPING_VIDEO, e);
      Log.e(getClass().getSimpleName(),
        "Exception stopping recording of video", e);
    }
    finally {
      isVideoRecording=false;
    }
  }

  private void setVideoFABToNormal() {
    fabPicture.setImageResource(
      R.drawable.cwac_cam2_ic_videocam);
    fabPicture.setColorNormalResId(
      R.color.cwac_cam2_picture_fab);
    fabPicture.setColorPressedResId(
      R.color.cwac_cam2_picture_fab_pressed);
    fabSwitch.setEnabled(canSwitchSources());
  }

  private boolean canSwitchSources() {
    if (isVideo() && !CameraConstraints.get().supportsFFCVideo()) {
      return(false);
    }

    return(!getArguments().getBoolean(ARG_FACING_EXACT_MATCH,
      false));
  }

  private boolean isVideo() {
    return(getArguments().getBoolean(ARG_IS_VIDEO, false));
  }

  private boolean showRuleOfThirds() {
    return(getArguments().getBoolean(ARG_RULE_OF_THIRDS, false));
  }

  private ChronoType getChronoType() {
    ChronoType chronoType=
      (ChronoType)getArguments().getSerializable(ARG_CHRONOTYPE);

    if (chronoType==null) {
      chronoType=ChronoType.NONE;
    }

    return (chronoType);
  }

  private void configureChronometer() {
    chronometer.setBase(SystemClock.elapsedRealtime());

    if (getChronoType()==ChronoType.COUNT_UP) {
      chronometer.setVisibility(View.VISIBLE);
      chronometer.start();
    }
    else if (getChronoType()==ChronoType.COUNT_DOWN) {
      if (Build.VERSION.SDK_INT>=Build.VERSION_CODES.N) {
        chronometer.setVisibility(View.VISIBLE);
        chronometer.setBase(SystemClock.elapsedRealtime()+
          getArguments().getInt(ARG_DURATION_LIMIT, 0));
        chronometer.setCountDown(true);
        chronometer.start();
      }
      else {
        reverseChronometer.setVisibility(View.VISIBLE);
        reverseChronometer
          .setOverallDuration(getArguments()
            .getInt(ARG_DURATION_LIMIT, 0)/1000);
        reverseChronometer.reset();
        reverseChronometer.run();
      }
    }
  }

  private void stopChronometers() {
    if (chronometer!=null) {
      chronometer.stop();
    }

    if (reverseChronometer!=null) {
      reverseChronometer.setListener(null);
      reverseChronometer.stop();
    }
  }

  private void prepController() {
    LinkedList<CameraView> cameraViews=new LinkedList<CameraView>();
    CameraView cv=(CameraView)previewStack.getChildAt(0);

    cv.setMirror(mirrorPreview);
    cameraViews.add(cv);

    for (int i=1; i<ctlr.getNumberOfCameras(); i++) {
      cv=new CameraView(getActivity());
      cv.setVisibility(View.INVISIBLE);
      cv.setMirror(mirrorPreview);
      previewStack.addView(cv);
      cameraViews.add(cv);
    }

    ctlr.setCameraViews(cameraViews);
  }

  // based on https://goo.gl/3IUM8K

  private void changeMenuIconAnimation(
    final FloatingActionMenu menu) {
    AnimatorSet set=new AnimatorSet();
    final ImageView v=menu.getMenuIconView();
    ObjectAnimator scaleOutX=
      ObjectAnimator.ofFloat(v, "scaleX", 1.0f, 0.2f);
    ObjectAnimator scaleOutY=
      ObjectAnimator.ofFloat(v, "scaleY", 1.0f, 0.2f);
    ObjectAnimator scaleInX=
      ObjectAnimator.ofFloat(v, "scaleX", 0.2f, 1.0f);
    ObjectAnimator scaleInY=
      ObjectAnimator.ofFloat(v, "scaleY", 0.2f, 1.0f);

    scaleOutX.setDuration(50);
    scaleOutY.setDuration(50);

    scaleInX.setDuration(150);
    scaleInY.setDuration(150);
    scaleInX.addListener(new AnimatorListenerAdapter() {
      @Override
      public void onAnimationStart(Animator animation) {
        v.setImageResource(menu.isOpened()
          ? R.drawable.cwac_cam2_ic_action_settings
          : R.drawable.cwac_cam2_ic_close);
        // yes, that seems backwards, but it works
        // presumably, opened state not yet toggled
      }
    });

    set.play(scaleOutX).with(scaleOutY);
    set.play(scaleInX).with(scaleInY).after(scaleOutX);
    set.setInterpolator(new OvershootInterpolator(2));
    menu.setIconToggleAnimatorSet(set);
  }

  private ZoomStyle getZoomStyle() {
    ZoomStyle result=
      (ZoomStyle)getArguments().getSerializable(ARG_ZOOM_STYLE);

    if (result==null) {
      result=ZoomStyle.NONE;
    }

    return (result);
  }

  private ScaleGestureDetector.OnScaleGestureListener scaleListener=
    new ScaleGestureDetector.SimpleOnScaleGestureListener() {
      @Override
      public void onScaleEnd(ScaleGestureDetector detector) {
        float scale=detector.getScaleFactor();
        int delta;

        if (scale>1.0f) {
          delta=PINCH_ZOOM_DELTA;
        }
        else if (scale<1.0f) {
          delta=-1*PINCH_ZOOM_DELTA;
        }
        else {
          return;
        }

        if (!inSmoothPinchZoom) {
          if (ctlr.changeZoom(delta)) {
            inSmoothPinchZoom=true;
          }
        }
      }
    };

  private SeekBar.OnSeekBarChangeListener seekListener=
    new SeekBar.OnSeekBarChangeListener() {
      @Override
      public void onProgressChanged(SeekBar seekBar,
                                    int progress,
                                    boolean fromUser) {
        if (fromUser) {
          if (ctlr.setZoom(progress)) {
            seekBar.setEnabled(false);
          }
        }
      }

      @Override
      public void onStartTrackingTouch(SeekBar seekBar) {
        // no-op
      }

      @Override
      public void onStopTrackingTouch(SeekBar seekBar) {
        // no-op
      }
    };
}