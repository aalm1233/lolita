package com.lolita.app.data

import androidx.lifecycle.SavedStateHandle
import com.lolita.app.data.file.ImageFileHelper
import com.lolita.app.data.tryon.ImageServiceStatus
import com.lolita.app.domain.model.ImageGenerationProvider
import com.lolita.app.ui.screen.material.MaterialWorkspaceViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

internal fun MaterialTestFixture.viewModel(handle: SavedStateHandle,
    changes: Flow<Int> = flowOf(0),
    readService: suspend () -> ImageServiceStatus = { ImageServiceStatus(ImageGenerationProvider.SEEDREAM,true) }
) = MaterialWorkspaceViewModel(handle,db.itemDao().getAllItems(),db.categoryDao().getAllCategories(),db.itemTryOnMaterialDao().observeAll(),
    db.imageTaskDao().observe(),db.tryOnDao().observe(),db.personalModelDao().observe(),coordinator,readService,changes,
    pin = { paths -> val lease=ImageFileHelper.pinImageReferences(context,paths);val release:suspend()->Unit={lease.release()};release },
    cleanup = { ImageFileHelper.cleanupUnreferencedImages(it);Unit })
