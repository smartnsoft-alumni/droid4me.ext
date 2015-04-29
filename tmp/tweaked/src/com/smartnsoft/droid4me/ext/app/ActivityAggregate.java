package com.smartnsoft.droid4me.ext.app;

import android.app.ActionBar;
import android.app.Activity;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentTransaction;
import com.smartnsoft.droid4me.app.SmartApplication;
import com.smartnsoft.droid4me.app.Smartable;
import com.smartnsoft.droid4me.ext.app.ActivityAnnotations.ActionBarBehavior;
import com.smartnsoft.droid4me.ext.app.ActivityAnnotations.ActionBarTitleBehavior;
import com.smartnsoft.droid4me.ext.app.ActivityAnnotations.ActivityAnnotation;
import com.smartnsoft.droid4me.log.Logger;
import com.smartnsoft.droid4me.log.LoggerFactory;
import com.smartnsoft.droid4me.support.v4.app.SmartFragment;

/**
 * @author Jocelyn Girard, Willy Noel
 * @since 2014.04.08
 */
public abstract class ActivityAggregate<SmartApplicationClass extends SmartApplication>
{

  protected final static Logger log = LoggerFactory.getInstance(ActivityAggregate.class);

  protected final Activity activity;

  protected final Smartable<?> smartable;

  private final ActivityAnnotation activityAnnotation;

  protected SmartFragment<?> fragment;

  public ActivityAggregate(Activity activity, Smartable<?> smartable, ActivityAnnotation activityAnnotation)
  {
    this.activity = activity;
    this.smartable = smartable;
    this.activityAnnotation = activityAnnotation;
  }

  @SuppressWarnings("unchecked")
  public SmartApplicationClass getApplication()
  {
    return (SmartApplicationClass) activity.getApplication();
  }

  /**
   * Open the specified fragment, the previous fragment is add to the back stack.
   *
   * @param fragmentClass
   */
  public final void openFragment(Class<? extends SmartFragment<?>> fragmentClass)
  {
    try
    {
      final FragmentTransaction fragmentTransaction = ((FragmentActivity) activity).getSupportFragmentManager().beginTransaction();
      fragment = fragmentClass.newInstance();
      fragmentTransaction.replace(activityAnnotation.fragmentContainerIdentifier(), fragment);
      fragmentTransaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
      fragmentTransaction.commit();
    }
    catch (Exception exception)
    {
      if (log.isErrorEnabled())
      {
        log.error("Unable to instanciate the fragment '" + fragmentClass.getSimpleName() + "'", exception);
      }
    }
  }

  public final SmartFragment<?> getOpennedFragment()
  {
    return fragment;
  }

  public final ActivityAnnotation getActivityAnnotation()
  {
    return activityAnnotation;
  }

  protected void onCreate()
  {
    if (activityAnnotation != null)
    {
      activity.setContentView(activityAnnotation.contentViewIdentifier());
      setActionBarBehavior();
      openParameterFragment();
    }
  }

  private void openParameterFragment()
  {
    if (activityAnnotation != null && activityAnnotation.fragmentClass() != null)
    {
      openFragment(activityAnnotation.fragmentClass());
    }
  }

  protected abstract Object getActionBar(Activity activity);

  protected void setActionBarBehavior()
  {
    final Object actionBarObject = getActionBar(activity);
    if (actionBarObject instanceof ActionBar)
    {
      final ActionBar actionBar = (ActionBar) actionBarObject;
      final ActionBarTitleBehavior actionBarTitleBehavior = activityAnnotation.actionBarTitleBehavior();
      switch (actionBarTitleBehavior)
      {
      case UseIcon:
        actionBar.setDisplayShowTitleEnabled(false);
        actionBar.setDisplayUseLogoEnabled(false);
        actionBar.setDisplayShowHomeEnabled(true);
        break;

      case UseLogo:
        actionBar.setDisplayShowTitleEnabled(false);
        actionBar.setDisplayUseLogoEnabled(true);
        actionBar.setDisplayShowHomeEnabled(true);
        break;

      default:
      case UseTitle:
        actionBar.setDisplayShowTitleEnabled(true);
        actionBar.setDisplayUseLogoEnabled(false);
        actionBar.setDisplayShowHomeEnabled(true);
        break;
      }
      final ActionBarBehavior actionBarUpBehavior = activityAnnotation.actionBarUpBehavior();
      switch (actionBarUpBehavior)
      {
      case ShowAsUp:
        actionBar.setHomeButtonEnabled(true);
        actionBar.setDisplayHomeAsUpEnabled(true);
        break;

      case ShowAsDrawer:
        actionBar.setHomeButtonEnabled(true);
        actionBar.setDisplayHomeAsUpEnabled(false);
        break;

      default:
      case None:
        actionBar.setHomeButtonEnabled(false);
        actionBar.setDisplayHomeAsUpEnabled(false);
        break;
      }
    }
  }

}