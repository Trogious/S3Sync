package net.swmud.trog.s3sync;

import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

public class RecyclerAdapter extends RecyclerView.Adapter<RecyclerAdapter.MyViewHolder> {
    private Uri[] mDataset;
    private Resolver resolver;

    // Provide a reference to the views for each data item
    // Complex data items may need more than one view per item, and
    // you provide access to all the views for a data item in a view holder
    public static class MyViewHolder extends RecyclerView.ViewHolder {
        // each data item is just a string in this case
        public TextView textView;
        public ProgressBar progressBar;
        public MyViewHolder(View v) {
            super(v);
            textView = v.findViewById(R.id.my_text_view);
            progressBar = v.findViewById(R.id.progressBar);
        }
    }

    public interface Resolver {
        String resolve(Uri uri);
    }

    // Provide a suitable constructor (depends on the kind of dataset)
    public RecyclerAdapter(Resolver resolver) {
        this.resolver = resolver;
    }

    // Create new views (invoked by the layout manager)
    @Override
    public RecyclerAdapter.MyViewHolder onCreateViewHolder(ViewGroup parent,
                                                           int viewType) {
        // create a new view
        final View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.my_text_view, parent, false);
        return new MyViewHolder(view);
    }

    // Replace the contents of a view (invoked by the layout manager)
    @Override
    public void onBindViewHolder(MyViewHolder holder, int position) {
        // - get element from your dataset at this position
        // - replace the contents of the view with that element
        holder.textView.setText(resolver.resolve(mDataset[position]));
        holder.progressBar.setProgress(75);

    }

    // Return the size of your dataset (invoked by the layout manager)
    @Override
    public int getItemCount() {
        return mDataset == null ? 0 : mDataset.length;
    }

    public void setDataset(Uri[] newSet) {
        mDataset = newSet.clone();
    }

    public Uri[] getDataset() {
        return mDataset;
    }
}