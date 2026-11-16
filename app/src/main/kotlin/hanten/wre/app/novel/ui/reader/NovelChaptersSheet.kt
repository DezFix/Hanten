package hanten.wre.app.novel.ui.reader

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.color.MaterialColors
import hanten.wre.app.databinding.ItemNovelChapterBinding
import hanten.wre.app.databinding.SheetNovelChaptersBinding
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import androidx.appcompat.R as appcompatR
import com.google.android.material.R as materialR

class NovelChaptersSheet : BottomSheetDialogFragment() {

	interface Callback {
		fun onChapterSelected(index: Int)

		/**
		 * Chapter titles come from the host screen. A novel can have thousands of
		 * chapters, and putting them into the fragment arguments overflowed the Binder
		 * buffer (TransactionTooLargeException) when the activity state was saved.
		 */
		fun observeChapterList(): Flow<ChapterList>
	}

	class ChapterList(
		val titles: List<String>,
		val downloaded: BooleanArray,
		val currentIndex: Int,
	)

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
		binding.chaptersList.layoutManager = LinearLayoutManager(requireContext())

		val source = callback ?: run {
			dismissAllowingStateLoss()
			return
		}
		val adapter = ChapterAdapter { index ->
			dismiss()
			callback?.onChapterSelected(index)
		}
		binding.chaptersList.adapter = adapter
		var isScrolledToCurrent = false
		viewLifecycleOwner.lifecycleScope.launch {
			viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
				source.observeChapterList().collect { list ->
					val titles = list.titles
					val downloaded = list.downloaded
					val currentIndex = list.currentIndex.coerceIn(0, (titles.size - 1).coerceAtLeast(0))
					binding.chaptersList.isVisible = titles.isNotEmpty()
					binding.textEmpty.isVisible = titles.isEmpty()
					// Update in place: replacing the adapter would jump the list back to the top.
					adapter.setData(titles, downloaded, currentIndex)
					if (titles.isNotEmpty() && !isScrolledToCurrent) {
						isScrolledToCurrent = true
						binding.chaptersList.scrollToPosition(currentIndex)
					}
				}
			}
		}
	}

	override fun onDestroyView() {
		super.onDestroyView()
		binding.chaptersList.adapter = null
		_binding = null
	}

	private class ChapterAdapter(
		private val onSelected: (Int) -> Unit,
	) : RecyclerView.Adapter<ChapterAdapter.ViewHolder>() {

		private var titles: List<String> = emptyList()
		private var downloaded: BooleanArray = BooleanArray(0)
		private var currentIndex: Int = -1

		@SuppressLint("NotifyDataSetChanged")
		fun setData(titles: List<String>, downloaded: BooleanArray, currentIndex: Int) {
			this.titles = titles
			this.downloaded = downloaded
			this.currentIndex = currentIndex
			notifyDataSetChanged()
		}

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
			holder.binding.imageDownloaded.isVisible = downloaded.getOrElse(position) { false }
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

		fun newInstance(): NovelChaptersSheet = NovelChaptersSheet()
	}
}
