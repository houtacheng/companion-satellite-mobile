import { combineRgb } from '@companion-module/base'
import type ModuleInstance from './main.js'

export type FeedbacksSchema = {
	connected: { type: 'boolean'; options: Record<string, never> }
	is_playing: { type: 'boolean'; options: Record<string, never> }
	is_paused: { type: 'boolean'; options: Record<string, never> }
	is_idle: { type: 'boolean'; options: Record<string, never> }
	playback_finished: { type: 'boolean'; options: Record<string, never> }
	muted: { type: 'boolean'; options: Record<string, never> }
	fullscreen: { type: 'boolean'; options: Record<string, never> }
	pip: { type: 'boolean'; options: Record<string, never> }
	ontop: { type: 'boolean'; options: Record<string, never> }
}

const white = combineRgb(255, 255, 255)

export function UpdateFeedbacks(self: ModuleInstance): void {
	self.setFeedbackDefinitions({
		connected: {
			name: 'PotPlayer 已連線',
			type: 'boolean',
			defaultStyle: { color: white, bgcolor: combineRgb(0, 130, 60) },
			options: [],
			callback: () => self.authenticated,
		},
		is_playing: {
			name: '正在播放',
			type: 'boolean',
			defaultStyle: { color: white, bgcolor: combineRgb(0, 150, 70) },
			options: [],
			callback: () => self.state.playback === 'playing',
		},
		is_paused: {
			name: '已暫停',
			type: 'boolean',
			defaultStyle: { color: combineRgb(0, 0, 0), bgcolor: combineRgb(255, 190, 0) },
			options: [],
			callback: () => self.state.playback === 'paused',
		},
		is_idle: {
			name: '沒有載入媒體',
			type: 'boolean',
			defaultStyle: { color: white, bgcolor: combineRgb(90, 90, 90) },
			options: [],
			callback: () => self.state.idle,
		},
		playback_finished: {
			name: '自然播放完畢',
			type: 'boolean',
			defaultStyle: { color: white, bgcolor: combineRgb(180, 0, 0) },
			options: [],
			callback: () => self.state.playbackFinished,
		},
		muted: {
			name: '已靜音',
			type: 'boolean',
			defaultStyle: { color: white, bgcolor: combineRgb(190, 0, 0) },
			options: [],
			callback: () => self.state.muted,
		},
		fullscreen: {
			name: '全螢幕已開啟',
			type: 'boolean',
			defaultStyle: { color: white, bgcolor: combineRgb(0, 90, 180) },
			options: [],
			callback: () => self.state.fullscreen,
		},
		pip: {
			name: '子母畫面已開啟',
			type: 'boolean',
			defaultStyle: { color: white, bgcolor: combineRgb(90, 60, 180) },
			options: [],
			callback: () => self.state.pip,
		},
		ontop: {
			name: '視窗已置頂',
			type: 'boolean',
			defaultStyle: { color: white, bgcolor: combineRgb(0, 120, 150) },
			options: [],
			callback: () => self.state.ontop,
		},
	})
}
