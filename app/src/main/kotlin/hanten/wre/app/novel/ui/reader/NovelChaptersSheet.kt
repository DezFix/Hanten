package hanten.wre.app.novel.ui.reader

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.color.MaterialColors
import hanten.wre.app.databinding.ItemNovelChapterBinding
import hanten.wre.app.databinding.SheetNovelChaptersBinding
import androidx.appcompat.R as appcompatR
import com.google.android.material.R as materialR

class NovelChaptersSheet : BottomSheetDialogFragment() {

	interface Callback {
		fun onChapterSelected(index: Int)
	}

	private var _binding: SheetNovelChaptersBinding? = null
	private val binding get() = _binding!!

	private var callback: Callback? = null

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?,
	): View {
		_binding = SheetNovelChaptersBinding.inflate(inflater, container, false)
		return binding.root
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		callback = activity as? Callback ?: parentFragment as? Callback

		val titles = arguments?.getStringArrayList(ARG_TITLES).orEmpty()
		val currentIndex = (arguments?.getInt(ARG_CURRENT_INDEX, 0) ?: 0)
			.coerceIn(0, (titles.size - 1).coerceAtLeast(0))

		binding.chaptersList.isVisible = titles.isNotEmpty()
		binding.textEmpty.isVisible = titles.isEmpty()

		if (titles.isNotEmpty()) {
			binding.chaptersList.layoutManager = LinearLayoutManager(requireContext())
			binding.chaptersList.adapter = ChapterAdapter(titles, currentIndex) { index ->
				dismiss()
				callback?.onChapterSelected(index)
			}
			binding.chaptersList.scrollToPosition(currentIndex)
		}
	}

	override fun onDestroyView() {
		super.onDestroyView()
		binding.chaptersList.adapter = null
		_binding = null
	}

	private class ChapterAdapter(
		private val titles: List<String>,
		private val currentIndex: Int,
		private val onSelected: (Int) -> Unit,
	) : RecyclerView.Adapter<ChapterAdapter.ViewHolder>() {

		class ViewHolder(val binding: ItemNovelChapterBinding) : RecyclerView.ViewHolder(binding.root)

		override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
			val inflater = LayoutInflater.from(parent.context)
			return ViewHolder(ItemNovelChapterBinding.inflate(inflater, parent, false))
		}

		override fun getItemCount(): Int = titles.size

		override fun onBindViewHolder(holder: ViewHolder, position: Int) {
			val isCurrent = position == currentIndex
			holder.binding.textChapterTitle.text = titles[position]
			holder.binding.textChapterTitle.setTextColor(
				MaterialColors.getColor(
					holder.itemView,
					if (isCurrent) appcompatR.attr.colorPrimary else materialR.attr.colorOnSurface,
				),
			)
			holder.itemView.isSelected = isCurrent
			holder.itemView.setOnClickListener {
				val index = holder.bindingAdapterPosition
				if (index != RecyclerView.NO_POSITION) {
					onSelected(index)
				}
			}
		}
	}

	companion object {
		const val TAG = "NovelChaptersSheet"

		private const val ARG_TITLES = "titles"
		private const val ARG_CURRENT_INDEX = "current_index"

		fun newInstance(titles: ArrayList<String>, currentIndex: Int): NovelChaptersSheet {
			return NovelChaptersSheet().apply {
				arguments = Bundle().apply {
					putStringArrayList(ARG_TITLES, titles)
					putInt(ARG_CURRENT_INDEX, currentIndex)
				}
			}
		}
	}
}
