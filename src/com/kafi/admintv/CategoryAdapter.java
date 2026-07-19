package com.kafi.admintv;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

public class CategoryAdapter extends BaseAdapter {

    public interface CategoryActionListener {
        void onEdit(FirestoreHelper.DocResult category);
        void onDelete(FirestoreHelper.DocResult category);
    }

    private Context context;
    private List<FirestoreHelper.DocResult> categories;
    private CategoryActionListener listener;

    public CategoryAdapter(Context context, CategoryActionListener listener) {
        this.context = context;
        this.categories = new ArrayList<FirestoreHelper.DocResult>();
        this.listener = listener;
    }

    public void setData(List<FirestoreHelper.DocResult> data) {
        this.categories = data;
        notifyDataSetChanged();
    }

    public List<FirestoreHelper.DocResult> getCategories() { return categories; }

    public List<String> getCategoryNames() {
        List<String> names = new ArrayList<String>();
        for (FirestoreHelper.DocResult c : categories) {
            String name = c.getString("name");
            if (!name.isEmpty()) names.add(name);
        }
        return names;
    }

    public int getCount() { return categories.size(); }
    public Object getItem(int pos) { return categories.get(pos); }
    public long getItemId(int pos) { return pos; }

    public View getView(int pos, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.item_category, parent, false);
        }

        final FirestoreHelper.DocResult cat = categories.get(pos);

        TextView name = (TextView) convertView.findViewById(R.id.catName);
        Button editBtn = (Button) convertView.findViewById(R.id.catEditBtn);
        Button delBtn = (Button) convertView.findViewById(R.id.catDelBtn);

        name.setText(cat.getString("name"));

        editBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { listener.onEdit(cat); }
        });

        delBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { listener.onDelete(cat); }
        });

        return convertView;
    }
}
