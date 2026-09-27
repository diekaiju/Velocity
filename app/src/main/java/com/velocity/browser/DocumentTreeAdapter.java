package com.velocity.browser;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DocumentTreeAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_FOLDER = 1;
    private static final int TYPE_FILE = 2;

    public interface OnItemInteractionListener {
        void onFileClicked(DocumentManager.DocItem item);
        void onFileOptions(DocumentManager.DocItem item, View anchor);
        void onFolderOptions(DocumentManager.DocItem item, View anchor);
        void onFolderToggled(DocumentManager.DocItem item, boolean isExpanded);
    }

    private final Context context;
    private final OnItemInteractionListener listener;
    private List<DocumentManager.DocItem> rootItems = new ArrayList<>();
    private final List<DocumentManager.DocItem> visibleItems = new ArrayList<>();
    private final Set<String> expandedPaths = new HashSet<>();
    private String selectedFilePath = null;
    private String searchQuery = "";

    public DocumentTreeAdapter(Context context, OnItemInteractionListener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void setData(List<DocumentManager.DocItem> items) {
        this.rootItems = items;
        rebuildVisibleList();
    }

    public void setSearchQuery(String query) {
        this.searchQuery = (query == null) ? "" : query.trim().toLowerCase();
        rebuildVisibleList();
    }

    public void setSelectedFile(File file) {
        if (file != null) {
            this.selectedFilePath = file.getAbsolutePath();
            // Automatically expand parent folders leading to this file
            File parent = file.getParentFile();
            while (parent != null) {
                expandedPaths.add(parent.getAbsolutePath());
                parent = parent.getParentFile();
            }
        } else {
            this.selectedFilePath = null;
        }
        rebuildVisibleList();
    }

    public void collapseAll() {
        expandedPaths.clear();
        rebuildVisibleList();
    }

    public void expandAll() {
        expandAllRecursive(rootItems);
        rebuildVisibleList();
    }

    private void expandAllRecursive(List<DocumentManager.DocItem> items) {
        if (items == null) return;
        for (DocumentManager.DocItem item : items) {
            if (item.isDirectory) {
                expandedPaths.add(item.file.getAbsolutePath());
                expandAllRecursive(item.children);
            }
        }
    }

    private void rebuildVisibleList() {
        visibleItems.clear();
        if (!searchQuery.isEmpty()) {
            filterRecursive(rootItems, visibleItems, searchQuery);
        } else {
            flattenRecursive(rootItems, visibleItems);
        }
        notifyDataSetChanged();
    }

    private void flattenRecursive(List<DocumentManager.DocItem> items, List<DocumentManager.DocItem> output) {
        if (items == null) return;
        for (DocumentManager.DocItem item : items) {
            output.add(item);
            if (item.isDirectory && expandedPaths.contains(item.file.getAbsolutePath())) {
                flattenRecursive(item.children, output);
            }
        }
    }

    private void filterRecursive(List<DocumentManager.DocItem> items, List<DocumentManager.DocItem> output, String query) {
        if (items == null) return;
        for (DocumentManager.DocItem item : items) {
            if (item.displayName.toLowerCase().contains(query)) {
                output.add(item);
            }
            if (item.isDirectory) {
                filterRecursive(item.children, output, query);
            }
        }
    }

    @Override
    public int getItemViewType(int position) {
        return visibleItems.get(position).isDirectory ? TYPE_FOLDER : TYPE_FILE;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(context);
        if (viewType == TYPE_FOLDER) {
            View v = inflater.inflate(R.layout.item_drawer_folder, parent, false);
            return new FolderViewHolder(v);
        } else {
            View v = inflater.inflate(R.layout.item_drawer_file, parent, false);
            return new FileViewHolder(v);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        DocumentManager.DocItem item = visibleItems.get(position);
        int indentDp = item.depth * 16;
        int indentPx = (int) (indentDp * context.getResources().getDisplayMetrics().density);

        if (holder instanceof FolderViewHolder) {
            FolderViewHolder fvh = (FolderViewHolder) holder;
            fvh.tvName.setText(item.displayName);
            fvh.viewIndent.getLayoutParams().width = indentPx;
            fvh.viewIndent.requestLayout();

            boolean isExpanded = expandedPaths.contains(item.file.getAbsolutePath());
            fvh.ivChevron.setImageResource(isExpanded ? R.drawable.ic_chevron_down : R.drawable.ic_chevron_right);

            fvh.itemView.setOnClickListener(v -> {
                String path = item.file.getAbsolutePath();
                if (expandedPaths.contains(path)) {
                    expandedPaths.remove(path);
                    if (listener != null) listener.onFolderToggled(item, false);
                } else {
                    expandedPaths.add(path);
                    if (listener != null) listener.onFolderToggled(item, true);
                }
                rebuildVisibleList();
            });

            fvh.btnMore.setOnClickListener(v -> {
                if (listener != null) listener.onFolderOptions(item, v);
            });

            fvh.itemView.setOnLongClickListener(v -> {
                if (listener != null) listener.onFolderOptions(item, v);
                return true;
            });

        } else if (holder instanceof FileViewHolder) {
            FileViewHolder fileVh = (FileViewHolder) holder;
            fileVh.tvName.setText(item.displayName);
            fileVh.viewIndent.getLayoutParams().width = indentPx;
            fileVh.viewIndent.requestLayout();

            boolean isSelected = selectedFilePath != null && selectedFilePath.equals(item.file.getAbsolutePath());

            if (isSelected) {
                fileVh.layoutContainer.setBackgroundResource(R.drawable.bg_drawer_selected_item);
                fileVh.tvName.setTextColor(Color.parseColor("#FFFFFF"));
            } else {
                fileVh.layoutContainer.setBackgroundColor(Color.TRANSPARENT);
                fileVh.tvName.setTextColor(Color.parseColor("#CAC4D0"));
            }

            // Show MD badge if applicable
            String ext = "";
            int dot = item.name.lastIndexOf('.');
            if (dot > 0) {
                ext = item.name.substring(dot + 1).toUpperCase();
            }
            if (!ext.isEmpty() && !ext.equalsIgnoreCase("MD")) {
                fileVh.tvBadge.setText(ext);
                fileVh.tvBadge.setVisibility(View.VISIBLE);
            } else {
                fileVh.tvBadge.setVisibility(View.GONE);
            }

            fileVh.layoutContainer.setOnClickListener(v -> {
                selectedFilePath = item.file.getAbsolutePath();
                notifyDataSetChanged();
                if (listener != null) listener.onFileClicked(item);
            });

            fileVh.btnMore.setOnClickListener(v -> {
                if (listener != null) listener.onFileOptions(item, v);
            });

            fileVh.layoutContainer.setOnLongClickListener(v -> {
                if (listener != null) listener.onFileOptions(item, v);
                return true;
            });
        }
    }

    @Override
    public int getItemCount() {
        return visibleItems.size();
    }

    static class FolderViewHolder extends RecyclerView.ViewHolder {
        final View viewIndent;
        final ImageView ivChevron;
        final TextView tvName;
        final ImageButton btnMore;

        FolderViewHolder(View v) {
            super(v);
            viewIndent = v.findViewById(R.id.viewFolderIndent);
            ivChevron = v.findViewById(R.id.ivFolderChevron);
            tvName = v.findViewById(R.id.tvFolderName);
            btnMore = v.findViewById(R.id.btnFolderMore);
        }
    }

    static class FileViewHolder extends RecyclerView.ViewHolder {
        final View viewIndent;
        final LinearLayout layoutContainer;
        final TextView tvName;
        final TextView tvBadge;
        final ImageButton btnMore;

        FileViewHolder(View v) {
            super(v);
            viewIndent = v.findViewById(R.id.viewFileIndent);
            layoutContainer = v.findViewById(R.id.layoutFileContainer);
            tvName = v.findViewById(R.id.tvFileName);
            tvBadge = v.findViewById(R.id.tvFileBadge);
            btnMore = v.findViewById(R.id.btnFileMore);
        }
    }
}
