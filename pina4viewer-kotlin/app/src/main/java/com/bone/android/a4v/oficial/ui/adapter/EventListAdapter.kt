package com.bone.android.a4v.oficial.ui.adapter

import android.graphics.Color
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
            binding.root.tag = event.id
            binding.ivSportIcon.setImageResource(event.getSportIconRes())

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

            binding.tvTitle.text = event.title
            binding.tvChannels.visibility = View.GONE

            val dt = listOfNotNull(
                event.date.takeIf { it.isNotBlank() },
                event.time.takeIf { it.isNotBlank() }
            ).joinToString(" ")
            binding.tvDateTime.text = dt
            binding.tvDateTime.visibility = if (dt.isNotBlank()) View.VISIBLE else View.GONE

            val isLiveHighlight = event.time.equals("Directo", ignoreCase = true) ||
                event.date.equals("24/7", ignoreCase = true)
            binding.tvDateTime.setTextColor(
                if (isLiveHighlight) Color.parseColor("#00E676") else Color.parseColor("#E0E0E0")
            )

            binding.cardRoot.setOnClickListener {
                if (event.channels.size == 1) {
                    onChannelClick(event.channels.first())
                } else {
                    onEventClick(event)
                }
            }

            binding.cardRoot.isFocusable = true
            binding.cardRoot.isClickable = true
            binding.cardRoot.onFocusChangeListener = View.OnFocusChangeListener { view, hasFocus ->
                val scale = if (hasFocus) 1.015f else 1.0f
                view.animate().cancel()
                view.animate().scaleX(scale).scaleY(scale).setDuration(100).start()
                view.elevation = if (hasFocus) 10f else 2f
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
