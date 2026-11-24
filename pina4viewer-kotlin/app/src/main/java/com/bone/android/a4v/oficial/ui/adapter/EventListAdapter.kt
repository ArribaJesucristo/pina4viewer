package com.bone.android.a4v.oficial.ui.adapter

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bone.android.a4v.oficial.R
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
            val ctx = binding.root.context
            binding.root.tag = event.id
            binding.ivSportIcon.setImageResource(event.getSportIconRes())

            val header = buildString {
                if (event.sport.isNotBlank()) append(event.sport.uppercase())
                if (event.competition.isNotBlank()) {
                    if (isNotEmpty()) append(" · ")
                    append(event.competition)
                }
            }
            binding.tvSportCompetition.text = header
            binding.tvSportCompetition.visibility = if (header.isNotBlank()) View.VISIBLE else View.GONE

            binding.tvTitle.text = event.title

            val is247 = event.date.equals("24/7", ignoreCase = true) ||
                event.sport.equals("DIRECTO 24/7", ignoreCase = true)
            val isLive = event.isLiveNow ||
                is247 ||
                event.time.equals("Directo", ignoreCase = true) ||
                event.time.equals("En Directo", ignoreCase = true)

            // Franja lateral de estado
            val accentColor = ContextCompat.getColor(
                ctx,
                if (isLive) R.color.live_green else R.color.gold_primary
            )
            ViewCompat.setBackgroundTintList(binding.viewAccentBar, ColorStateList.valueOf(accentColor))

            // Badge horario o EN DIRECTO
            val dt = listOfNotNull(
                event.date.takeIf { it.isNotBlank() },
                event.time.takeIf { it.isNotBlank() }
            ).joinToString(" ")

            if (is247) {
                binding.tvDateTime.text = "● 24/7"
                binding.tvDateTime.setBackgroundResource(R.drawable.bg_live_badge)
                binding.tvDateTime.setTextColor(ContextCompat.getColor(ctx, R.color.background_dark))
                binding.tvDateTime.visibility = View.VISIBLE
            } else if (isLive) {
                binding.tvDateTime.text = "● EN DIRECTO"
                binding.tvDateTime.setBackgroundResource(R.drawable.bg_live_badge)
                binding.tvDateTime.setTextColor(ContextCompat.getColor(ctx, R.color.background_dark))
                binding.tvDateTime.visibility = View.VISIBLE
            } else if (dt.isNotBlank()) {
                binding.tvDateTime.text = dt
                binding.tvDateTime.setBackgroundResource(R.drawable.bg_time_badge)
                binding.tvDateTime.setTextColor(ContextCompat.getColor(ctx, R.color.text_primary))
                binding.tvDateTime.visibility = View.VISIBLE
            } else {
                binding.tvDateTime.visibility = View.GONE
            }

            // Subtítulo informativo
            binding.tvSubInfo.text = when {
                is247 -> "Emisión continua"
                isLive && event.time.matches(CLOCK_TIME_REGEX) -> "Inicio ${event.time}"
                isLive -> "En emisión"
                event.channels.size > 1 -> "${event.channels.size} opciones disponibles"
                event.channels.size == 1 -> "Acceso directo 1 clic"
                else -> "Programado"
            }

            // Badge de canales disponibles
            when {
                event.channels.isEmpty() -> {
                    binding.tvChannels.visibility = View.GONE
                }
                event.channels.size == 1 -> {
                    binding.tvChannels.visibility = View.VISIBLE
                    binding.tvChannels.text = "⚡ 1 clic"
                    binding.tvChannels.setTextColor(ContextCompat.getColor(ctx, R.color.live_green))
                }
                else -> {
                    binding.tvChannels.visibility = View.VISIBLE
                    binding.tvChannels.text = "▶ ${event.channels.size} ch"
                    binding.tvChannels.setTextColor(ContextCompat.getColor(ctx, R.color.gold_primary))
                }
            }

            binding.cardRoot.setOnClickListener {
                if (event.channels.size == 1) {
                    onChannelClick(event.channels.first())
                } else {
                    onEventClick(event)
                }
            }

            // Resetear escala/elevación/color en reciclaje para evitar artefactos visuales al hacer scroll
            val hasFocus = binding.cardRoot.hasFocus()
            binding.cardRoot.animate().cancel()
            binding.cardRoot.scaleX = if (hasFocus) 1.02f else 1.0f
            binding.cardRoot.scaleY = if (hasFocus) 1.02f else 1.0f
            binding.cardRoot.elevation = if (hasFocus) 12f else 2f
            binding.cardRoot.setCardBackgroundColor(
                ContextCompat.getColor(ctx, if (hasFocus) R.color.card_focused else R.color.card_background)
            )

            binding.cardRoot.isFocusable = true
            binding.cardRoot.isClickable = true
            binding.cardRoot.setOnFocusChangeListener { view, focused ->
                val scale = if (focused) 1.02f else 1.0f
                view.animate().cancel()
                view.animate().scaleX(scale).scaleY(scale).setDuration(110).start()
                view.elevation = if (focused) 12f else 2f
                (view as? CardView)?.setCardBackgroundColor(
                    ContextCompat.getColor(
                        view.context,
                        if (focused) R.color.card_focused else R.color.card_background
                    )
                )
            }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<EventItem>() {
        private val CLOCK_TIME_REGEX = Regex("^\\d{1,2}:\\d{2}.*$")

        override fun areItemsTheSame(oldItem: EventItem, newItem: EventItem): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: EventItem, newItem: EventItem): Boolean =
            oldItem == newItem
    }
}
