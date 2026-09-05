package com.bone.android.a4v.oficial.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bone.android.a4v.oficial.data.model.ChannelItem
import com.bone.android.a4v.oficial.data.model.EventItem
import com.bone.android.a4v.oficial.databinding.ItemEventBinding

class EventListAdapter(
    private val onEventClick: (EventItem) -> Unit,
    private val onChannelClick: (ChannelItem) -> Unit
) : ListAdapter<EventItem, EventListAdapter.EventViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventViewHolder {
        val binding = ItemEventBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return EventViewHolder(binding)
    }

    override fun onBindViewHolder(holder: EventViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class EventViewHolder(private val binding: ItemEventBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(event: EventItem) {
            // Sport Icon
            binding.ivSportIcon.setImageResource(event.getSportIconRes())

            // Sport & Competition Header
            val header = buildString {
                if (event.sport.isNotBlank()) append(event.sport.uppercase())
                if (event.competition.isNotBlank()) {
                    if (isNotEmpty()) append(" (")
                    append(event.competition.uppercase())
                    if (isNotEmpty()) append(")")
                }
            }
            binding.tvSportCompetition.text = header
            binding.tvSportCompetition.visibility = if (header.isNotBlank()) View.VISIBLE else View.GONE

            // Match Title
            binding.tvTitle.text = event.title

            // Channels text hidden on cards for a clean, compact view
            binding.tvChannels.visibility = View.GONE

            // Date and Time
            val dt = listOfNotNull(
                event.date.takeIf { it.isNotBlank() },
                event.time.takeIf { it.isNotBlank() }
            ).joinToString(" ")
            binding.tvDateTime.text = dt
            binding.tvDateTime.visibility = if (dt.isNotBlank()) View.VISIBLE else View.GONE

            // Click handling
            binding.cardRoot.setOnClickListener {
                if (event.channels.size == 1) {
                    onChannelClick(event.channels.first())
                } else {
                    onEventClick(event)
                }
            }

            // TV remote focus setup
            binding.cardRoot.isFocusable = true
            binding.cardRoot.isClickable = true
            binding.cardRoot.setOnKeyListener { _, keyCode, event ->
                if (event.action == android.view.KeyEvent.ACTION_DOWN && keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP && bindingAdapterPosition == 0) {
                    binding.root.rootView.findViewById<android.view.View>(com.bone.android.a4v.oficial.R.id.etSearch)?.requestFocus()
                    true
                } else {
                    false
                }
            }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<EventItem>() {
        override fun areItemsTheSame(oldItem: EventItem, newItem: EventItem): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: EventItem, newItem: EventItem): Boolean =
            oldItem == newItem
    }
}
