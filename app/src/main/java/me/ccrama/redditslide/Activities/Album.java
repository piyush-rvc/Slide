package me.ccrama.redditslide.Activities;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.NotificationCompat;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.afollestad.materialdialogs.AlertDialogWrapper;
import com.afollestad.materialdialogs.MaterialDialog;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import me.ccrama.redditslide.Adapters.AlbumView;
import me.ccrama.redditslide.ColorPreferences;
import me.ccrama.redditslide.Fragments.BlankFragment;
import me.ccrama.redditslide.Fragments.FolderChooserDialogCreate;
import me.ccrama.redditslide.ImgurAlbum.AlbumUtils;
import me.ccrama.redditslide.ImgurAlbum.Image;
import me.ccrama.redditslide.R;
import me.ccrama.redditslide.Reddit;
import me.ccrama.redditslide.SettingValues;
import me.ccrama.redditslide.Views.PreCachingLayoutManager;
import me.ccrama.redditslide.Views.ToolbarColorizeHelper;


/**
 * Created by ccrama on 3/5/2015.
 * <p/>
 * This class is responsible for accessing the Imgur api to get the album json data
 * from a URL or Imgur hash. It extends FullScreenActivity and supports swipe from anywhere.
 */
public class Album extends FullScreenActivity implements FolderChooserDialogCreate.FolderCallback {
    public static final String EXTRA_URL = "url";
    private List<Image> images;

    @Override
    public void onFolderSelection(FolderChooserDialogCreate dialog, File folder) {
        if (folder != null) {
            Reddit.appRestart.edit().putString("imagelocation", folder.getAbsolutePath().toString()).apply();
            Toast.makeText(this, "Images will be saved to " + folder.getAbsolutePath(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == android.R.id.home) {
            onBackPressed();
        }
        if (id == R.id.slider) {
            SettingValues.albumSwipe = true;
            SettingValues.prefs.edit().putBoolean(SettingValues.PREF_ALBUM_SWIPE, true).apply();
            Intent i = new Intent(Album.this, AlbumPager.class);
            i.putExtra("url", url);
            startActivity(i);
            finish();
        }
        if (id == R.id.grid) {
            mToolbar.findViewById(R.id.grid).callOnClick();
        }
        if (id == R.id.external) {
            Reddit.defaultShare(url, this);
        }
        if (id == R.id.download) {
            final MaterialDialog d = new MaterialDialog.Builder(Album.this)
                    .title("Saving album")
                    .progress(false, images.size())
                    .show();
            new AsyncTask<Void, Void, Void>() {
                @Override
                protected Void doInBackground(Void... params) {
                    if (images != null && !images.isEmpty()) {
                        for (final Image elem : images) {
                            saveImageGallery(((Reddit) getApplicationContext()).getImageLoader().loadImageSync(elem.getImageUrl()), elem.getImageUrl());
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    d.setProgress(d.getCurrentProgress() + 1);

                                }
                            });
                        }
                        d.dismiss();
                    }
                    return null;
                }
            }.execute();

        }

        return super.onOptionsItemSelected(item);
    }

    public void showFirstDialog() {
        try {
            new AlertDialogWrapper.Builder(this)
                    .setTitle(R.string.set_save_location)
                    .setMessage(R.string.set_save_location_msg)
                    .setPositiveButton(R.string.btn_yes, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            new FolderChooserDialogCreate.Builder(Album.this)
                                    .chooseButton(R.string.btn_select)  // changes label of the choose button
                                    .initialPath(Environment.getExternalStorageDirectory().getPath())  // changes initial path, defaults to external storage directory
                                    .show();
                        }
                    })
                    .setNegativeButton(R.string.btn_no, null)
                    .show();
        } catch (Exception ignored) {

        }
    }

    public void showNotifPhoto(final File localAbsoluteFilePath, final Bitmap loadedImage) {
        MediaScannerConnection.scanFile(Album.this, new String[]{localAbsoluteFilePath.getAbsolutePath()}, null, new MediaScannerConnection.OnScanCompletedListener() {
            public void onScanCompleted(String path, Uri uri) {

                final Intent shareIntent = new Intent(Intent.ACTION_VIEW);
                shareIntent.setDataAndType(Uri.fromFile(localAbsoluteFilePath), "image/*");
                PendingIntent contentIntent = PendingIntent.getActivity(Album.this, 0, shareIntent, PendingIntent.FLAG_CANCEL_CURRENT);


                Notification notif = new NotificationCompat.Builder(Album.this)
                        .setContentTitle(getString(R.string.info_photo_saved))
                        .setSmallIcon(R.drawable.notif)
                        .setLargeIcon(loadedImage)
                        .setContentIntent(contentIntent)
                        .setStyle(new NotificationCompat.BigPictureStyle()
                                .bigPicture(loadedImage)).build();


                NotificationManager mNotificationManager =
                        (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                mNotificationManager.notify(1, notif);
                loadedImage.recycle();
            }

        });
    }

    public void showErrorDialog() {
        new AlertDialogWrapper.Builder(Album.this)
                .setTitle(R.string.err_something_wrong)
                .setMessage(R.string.err_couldnt_save_choose_new)
                .setPositiveButton(R.string.btn_yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        new FolderChooserDialogCreate.Builder(Album.this)
                                .chooseButton(R.string.btn_select)  // changes label of the choose button
                                .initialPath(Environment.getExternalStorageDirectory().getPath())  // changes initial path, defaults to external storage directory
                                .show();
                    }
                })
                .setNegativeButton(R.string.btn_no, null)
                .show();
    }

    private void saveImageGallery(final Bitmap bitmap, String URL) {
        if (Reddit.appRestart.getString("imagelocation", "").isEmpty()) {
            showFirstDialog();
        } else if (!new File(Reddit.appRestart.getString("imagelocation", "")).exists()) {
            showErrorDialog();
        } else {
            File f = new File(Reddit.appRestart.getString("imagelocation", "") + File.separator + UUID.randomUUID().toString() + ".png");


            FileOutputStream out = null;
            try {
                f.createNewFile();
                out = new FileOutputStream(f);
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            } catch (Exception e) {
                e.printStackTrace();
                showErrorDialog();
            } finally {
                try {
                    if (out != null) {
                        out.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    showErrorDialog();
                }
            }
        }

    }

    public String url;

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.album_vertical, menu);

        //   if (mShowInfoButton) menu.findItem(R.id.action_info).setVisible(true);
        //   else menu.findItem(R.id.action_info).setVisible(false);

        return true;
    }

    public OverviewPagerAdapter album;

    public void onCreate(Bundle savedInstanceState) {
        overrideSwipeFromAnywhere();
        super.onCreate(savedInstanceState);
        getTheme().applyStyle(new ColorPreferences(this).getDarkThemeSubreddit(ColorPreferences.FONT_STYLE), true);
        setContentView(R.layout.album);

        final ViewPager pager = (ViewPager) findViewById(R.id.images);

        album = new OverviewPagerAdapter(getSupportFragmentManager());
        pager.setAdapter(album);
        pager.setCurrentItem(1);
        pager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
                                          @Override
                                          public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
                                              if (position == 0 && positionOffsetPixels == 0) {
                                                  finish();
                                              }
                                              if (position == 0) {
                                                  if (((OverviewPagerAdapter) pager.getAdapter()).blankPage != null)
                                                      ((OverviewPagerAdapter) pager.getAdapter()).blankPage.doOffset(positionOffset);
                                                  ((OverviewPagerAdapter) pager.getAdapter()).blankPage.realBack.setBackgroundColor(adjustAlpha(positionOffset * 0.7f));
                                              }
                                          }

                                          @Override
                                          public void onPageSelected(int position) {
                                          }

                                          @Override
                                          public void onPageScrollStateChanged(int state) {

                                          }
                                      }

        );

        if (!Reddit.appRestart.contains("tutorialSwipe")) {
            startActivityForResult(new Intent(this, SwipeTutorial.class), 3);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 3) {
            Reddit.appRestart.edit().putBoolean("tutorialSwipe", true).apply();

        }
    }

    public class OverviewPagerAdapter extends FragmentStatePagerAdapter {
        public BlankFragment blankPage;
        public AlbumFrag album;

        public OverviewPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int i) {
            if (i == 0) {
                blankPage = new BlankFragment();
                return blankPage;
            } else {
                album = new AlbumFrag();
                return album;

            }
        }

        @Override
        public int getCount() {

            return 2;
        }

    }

    public int adjustAlpha(float factor) {
        int alpha = Math.round(Color.alpha(Color.BLACK) * factor);
        int red = Color.red(Color.BLACK);
        int green = Color.green(Color.BLACK);
        int blue = Color.blue(Color.BLACK);
        return Color.argb(alpha, red, green, blue);
    }

    public static class AlbumFrag extends Fragment {
        View rootView;
        public RecyclerView recyclerView;

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            rootView = inflater.inflate(
                    R.layout.fragment_verticalalbum, container, false);

            final PreCachingLayoutManager mLayoutManager;
            mLayoutManager = new PreCachingLayoutManager(getActivity());
            recyclerView = (RecyclerView) rootView.findViewById(R.id.images);
            recyclerView.setLayoutManager(mLayoutManager);
            ((Album) getActivity()).url = getActivity().getIntent().getExtras().getString(EXTRA_URL, "");

            new LoadIntoRecycler(((Album) getActivity()).url, getActivity()).execute();
            ((Album) getActivity()).mToolbar = (Toolbar) rootView.findViewById(R.id.toolbar);
            ((Album) getActivity()).mToolbar.setTitle(R.string.type_album);
            ToolbarColorizeHelper.colorizeToolbar(((Album) getActivity()).mToolbar, Color.WHITE, (getActivity()));
            ((Album) getActivity()).setSupportActionBar(((Album) getActivity()).mToolbar);
            ((Album) getActivity()).getSupportActionBar().setDisplayHomeAsUpEnabled(true);

            ((Album) getActivity()).mToolbar.setPopupTheme(new ColorPreferences(((Album) getActivity())).getDarkThemeSubreddit(ColorPreferences.FONT_STYLE));
            return rootView;
        }

        public class LoadIntoRecycler extends AlbumUtils.GetAlbumWithCallback {

            String url;

            public LoadIntoRecycler(@NotNull String url, @NotNull Activity baseActivity) {
                super(url, baseActivity);
                this.url = url;
            }

            @Override
            public void onError(){
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            new AlertDialogWrapper.Builder(getActivity())
                                    .setTitle("Album not found")
                                    .setMessage("Would you like to open the link in browser?")
                                    .setNegativeButton(R.string.btn_no, new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            getActivity().finish();
                                        }
                                    }).setCancelable(false)
                                    .setPositiveButton(R.string.btn_yes, new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            Intent i = new Intent(getActivity(), Website.class);
                                            i.putExtra(Website.EXTRA_URL, url);
                                            startActivity(i);
                                            getActivity().finish();
                                        }
                                    }).show();
                        } catch(Exception e){

                        }
                    }
                });

            }

            @Override
            public void doWithData(final List<Image> jsonElements) {
                super.doWithData(jsonElements);
                getActivity().findViewById(R.id.progress).setVisibility(View.GONE);
                ((Album) getActivity()).images = new ArrayList<>(jsonElements);
                AlbumView adapter = new AlbumView(baseActivity, ((Album) getActivity()).images, getActivity().findViewById(R.id.toolbar).getHeight());
                recyclerView.setAdapter(adapter);

            }
        }
    }

}