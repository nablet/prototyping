package com.cloudstaff.myapplication.utils.listview

import android.R
import android.widget.ArrayAdapter
import android.widget.ListView

object ListViewHelper {
	fun <T> setup(
		listView: ListView,
		items: List<T>,
		layoutRes: Int = R.layout.simple_list_item_1,
		onClick: ((item: T, position: Int) -> Unit)? = null,
	) {
		val adapter = ArrayAdapter(listView.context, layoutRes, items)
		listView.adapter = adapter

		listView.setOnItemClickListener { _, _, pos, _ ->
			onClick?.invoke(items[pos], pos)
		}
	}
}