package com.smartnsoft.droid4me.ext.widget;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.util.SparseArray;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.ListView;

import com.smartnsoft.droid4me.framework.SmartAdapters.BusinessViewWrapper;
import com.smartnsoft.droid4me.framework.SmartAdapters.ObjectEvent;
import com.smartnsoft.droid4me.framework.SmartAdapters.SmartListAdapter;
import com.smartnsoft.droid4me.log.Logger;
import com.smartnsoft.droid4me.log.LoggerFactory;

/**
 * @author ɉdouard Mercier
 * @since 2013.03.20
 */
public class SmartRowListAdapter<ViewClass extends View>
    extends SmartListAdapter<ViewClass>
{

  public static interface ColumnsIndicator
  {

    int getColumnsCount(int row);

    int getHorizontalPaddingForRow(int row);

  }

  public abstract static class HorizontalPaddingColumnsIndicator
      implements ColumnsIndicator
  {

    public final int horizontalPadding;

    public HorizontalPaddingColumnsIndicator(int horizontalPadding)
    {
      this.horizontalPadding = horizontalPadding;
    }

    @Override
    public int getHorizontalPaddingForRow(int row)
    {
      return horizontalPadding;
    }

  }

  public static class ConstantColumnsIndicator
      extends HorizontalPaddingColumnsIndicator
  {

    public final int columnsCount;

    public ConstantColumnsIndicator(int horizontalPadding, int columnsCount)
    {
      super(horizontalPadding);
      this.columnsCount = columnsCount;
    }

    @Override
    public int getColumnsCount(int row)
    {
      return columnsCount;
    }

  }

  private final static class EmptyAttributes
  {

    public EmptyAttributes(View view)
    {
    }

  }

  public final static class EmptyWrapper
      extends BusinessViewWrapper<Integer>
  {

    public EmptyWrapper(Integer businessObject)
    {
      super(businessObject);
    }

    @Override
    public int getType(int position, Integer businessObject)
    {
      return businessObject;
    }

    @Override
    public boolean isEnabled(Integer businessObject)
    {
      return false;
    }

    @Override
    protected View createNewView(Activity activity, ViewGroup parent, Integer businessObject)
    {
      return new View(activity.getApplicationContext());
    }

    @Override
    protected Object extractNewViewAttributes(Activity activity, View view, Integer businessObject)
    {
      return new EmptyAttributes(view);
    }

    @Override
    protected void updateView(Activity activity, Object viewAttributes, View view, Integer businessObject, int position)
    {
    }

  }

  private final static class RowBusinessViewAttributes<BusinessObjectClass>
  {

    private final LinearLayout linearLayout;

    private View[] views;

    public RowBusinessViewAttributes(LinearLayout linearLayout)
    {
      this.linearLayout = linearLayout;
    }

    public void update(final Activity activity, List<BusinessViewWrapper<BusinessObjectClass>> businessObject, int horizontalPadding)
    {
      if (horizontalPadding > 0)
      {
        linearLayout.setPadding(horizontalPadding, 0, horizontalPadding, 0);
      }
      if (views == null)
      {
        views = new View[businessObject.size()];
        int index = 0;
        for (BusinessViewWrapper<BusinessObjectClass> wrapper : businessObject)
        {
          final View view = wrapper.getNewView(linearLayout, activity);
          views[index] = view;
          // All cells will have the same width
          final ViewGroup.LayoutParams viewLayoutParams = null;
          final LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(0, viewLayoutParams != null ? viewLayoutParams.height
              : LayoutParams.WRAP_CONTENT, 1);
          views[index].setFocusable(true);
          views[index].setClickable(true);
          linearLayout.addView(view, layoutParams);
          index++;
        }
      }
      if (views.length != businessObject.size())
      {
        if (log.isErrorEnabled())
        {
          log.error("Roger, we've got a problem with the SmartRowListAdapter!");
        }
      }
      for (int index = 0; index < views.length; index++)
      {
        final BusinessViewWrapper<BusinessObjectClass> wrapper = businessObject.get(index);
        wrapper.updateView(activity, views[index], index);
        final int finalIndex = index;
        views[index].setOnClickListener(new View.OnClickListener()
        {
          @Override
          public void onClick(View view)
          {
            wrapper.onObjectEvent(activity, view, ObjectEvent.Clicked, finalIndex);
          }
        });
      }
    }

  }

  public final static class RowLinearLayout
      extends LinearLayout
  {

    public Boolean isLayoutRequested;

    public RowLinearLayout(Context context)
    {
      super(context);
    }

    @Override
    public void requestLayout()
    {
      super.requestLayout();
      System.out.println("requestLayout() -> " + this.toString());
    }

    public boolean isLayoutRequested()
    {
      if (isLayoutRequested != null)
      {
        return isLayoutRequested;
      }
      return super.isLayoutRequested();
    }

  }

  public static class RowBusinessViewWrapper<BusinessObjectClass>
      extends BusinessViewWrapper<List<BusinessViewWrapper<BusinessObjectClass>>>
  {

    private final int type;

    private final int horizontalPadding;

    protected RowBusinessViewWrapper(List<BusinessViewWrapper<BusinessObjectClass>> businessObject, int type, int horizontalPadding)
    {
      super(businessObject);
      this.type = type;
      this.horizontalPadding = horizontalPadding;
    }

    @Override
    public int getType(int position, List<BusinessViewWrapper<BusinessObjectClass>> businessObjectClass)
    {
      return type;
    }

    @Override
    protected View createNewView(Activity activity, ViewGroup parent, List<BusinessViewWrapper<BusinessObjectClass>> businessObject)
    {
      final LinearLayout linearLayout = new RowLinearLayout(activity.getApplicationContext());
      linearLayout.setOrientation(LinearLayout.HORIZONTAL);
      return linearLayout;
    }

    @Override
    protected Object extractNewViewAttributes(Activity activity, View view, List<BusinessViewWrapper<BusinessObjectClass>> businessObject)
    {
      return new RowBusinessViewAttributes<BusinessObjectClass>((LinearLayout) view);
    }

    @SuppressWarnings("unchecked")
    @Override
    protected void updateView(Activity activity, Object viewAttributes, View view, List<BusinessViewWrapper<BusinessObjectClass>> businessObject, int position)
    {
      ((RowBusinessViewAttributes<BusinessObjectClass>) viewAttributes).update(activity, businessObject, horizontalPadding);
    }

  }

  private static final Logger log = LoggerFactory.getInstance("SmartAdapters");

  /**
   * The maximum number of different view types that this component supports.
   */
  private final static int maximumDifferentTypes = 20;

  /**
   * A map which holds the already computed row types: the key is the actual total type, and the value is the canonical value
   */
  @SuppressLint("UseSparseArrays")
  private final SparseArray<Integer> types = new SparseArray<Integer>();

  public static int getRow(int position, ColumnsIndicator columnsIndicator, AtomicInteger lastRowPosition, AtomicInteger lastRowColumnsCount)
  {
    lastRowPosition.set(position);
    int currentPosition = 0;
    int row = 0;
    while (currentPosition < position)
    {
      final int columnsCount = columnsIndicator.getColumnsCount(row);
      currentPosition += columnsCount;
      if ((lastRowPosition.get() - columnsCount) > 0)
      {
        lastRowPosition.set(lastRowPosition.get() - columnsCount);
      }
      lastRowColumnsCount.set(columnsCount);
      row++;
    }
    return row;
  }

  /**
   * @param view
   * @return the individual tags of the provided {@code view} children, which have been wrapped
   */
  public static List<Object> getTags(View view)
  {
    final ArrayList<Object> tags = new ArrayList<Object>();
    if (view instanceof ViewGroup)
    {
      final ViewGroup viewGroup = (ViewGroup) view;
      for (int index = 0; index < viewGroup.getChildCount(); index++)
      {
        tags.add(viewGroup.getChildAt(index).getTag());
      }
    }
    return tags;
  }

  public SmartRowListAdapter(Activity activity, int viewTypeCount)
  {
    super(activity, viewTypeCount);
  }

  @Override
  public void setAdapter(ListView listView)
  {
    listView.setItemsCanFocus(true);
    super.setAdapter(listView);
  }

  public List<BusinessViewWrapper<?>> convertWrappers(List<? extends BusinessViewWrapper<?>> wrappers, ColumnsIndicator columnsIndicator)
  {
    final List<BusinessViewWrapper<?>> conversiondWrappers = new ArrayList<BusinessViewWrapper<?>>();
    int index = 0;
    int row = 0;
    while (true)
    {
      final List<BusinessViewWrapper<?>> rowWrappers = new ArrayList<BusinessViewWrapper<?>>();
      int totalType = 0;
      final int columnsCount = columnsIndicator.getColumnsCount(row);
      for (int column = 0; column < columnsCount; column++)
      {
        final int type;
        if (index >= wrappers.size())
        {
          final EmptyWrapper wrapper = new EmptyWrapper(0);
          rowWrappers.add(wrapper);
          type = wrapper.getType(column);
        }
        else
        {
          final BusinessViewWrapper<?> wrapper = wrappers.get(index);
          type = wrapper.getType(column) + 1;
          rowWrappers.add(wrapper);
        }
        totalType += Math.max(1, column * SmartRowListAdapter.maximumDifferentTypes) * type;
        index++;
      }
      Integer canonicalType = (Integer) types.get(totalType);
      if (canonicalType == null)
      {
        canonicalType = types.size();
        if (log.isDebugEnabled())
        {
          log.debug("Adding the view type " + canonicalType + " corresponding to the computed total type value " + totalType);
        }
        types.put(totalType, canonicalType);
      }
      conversiondWrappers.add(getRowBusinessViewWrapper(rowWrappers, canonicalType, columnsIndicator.getHorizontalPaddingForRow(row)));
      if (index >= wrappers.size())
      {
        break;
      }
      row++;
    }
    // viewTypeCount.set(types.size());
    return conversiondWrappers;
  }

  @SuppressWarnings({ "rawtypes", "unchecked" })
  protected RowBusinessViewWrapper<?> getRowBusinessViewWrapper(List<BusinessViewWrapper<?>> rowWrappers, Integer canonicalType, int horizontalPadding)
  {
    return new RowBusinessViewWrapper(rowWrappers, canonicalType, horizontalPadding);
  }

  public BusinessViewWrapper<?> createEmptyWrapper(int viewType)
  {
    return new EmptyWrapper(viewType);
  }

}