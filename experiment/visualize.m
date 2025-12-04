function visualize(trace, phi_str, up_robM, low_robM, up_optCau, low_optCau, outfile, signal_names)
%PLOT_STL_ANALYSIS Generic plotting routine for STL analysis results
%
% [動的更新バージョン]
% 1つのFigureウィンドウを再利用し、プロットデータのみを更新する。
%
% 引数:
%   signal_names: カンマ区切りのシグナル名文字列 (例: 'temp,cooling')

    % --- 引数のデフォルト値設定 ---
    if nargin < 7
        outfile = 'Figure_output.png'; % 保存ファイル名（毎回上書き）
    end
    if nargin < 8 || isempty(signal_names)
        signal_names = ''; % デフォルトは空
    end

    % --- データ抽出 (時間とシグナル数) ---
    t = trace(1,:);
    num_signals = size(trace,1) - 1;

    % --- シグナル名をパース ---
    if ~isempty(signal_names)
        signal_name_list = strsplit(signal_names, ',');
        % 前後の空白を削除
        signal_name_list = strtrim(signal_name_list);

        % --- 先頭に time が含まれている場合は取り除く ---
        % trace の 1 行目が time なので、signal_names に time が入っていると
        % プロットと名前の対応がずれてしまう。ユーザーは time を省略してよい。
        if ~isempty(signal_name_list)
            % 条件: 名前配列の長さが期待するシグナル数+1（time を含む）
            % または先頭が 'time'/'t' と明示的に書かれている場合
            if length(signal_name_list) == num_signals + 1 || strcmpi(signal_name_list{1}, 'time') || strcmpi(signal_name_list{1}, 't')
                signal_name_list = signal_name_list(2:end);
            end
        end
    else
        % デフォルトのシグナル名
        signal_name_list = cell(1, num_signals);
        for s = 1:num_signals
            signal_name_list{s} = sprintf('Signal %d', s);
        end
    end

    % --- デバッグ情報出力 ---
    fprintf('\n=== DEBUG INFO ===\n');
    fprintf('Original signal names string: %s\n', signal_names);
    fprintf('Processed signal name list: %s\n', strjoin(signal_name_list, ','));
    fprintf('Number of signals: %d\n', num_signals);
    fprintf('Time array length: %d\n', length(t));
    fprintf('up_robM length: %d\n', length(up_robM));
    fprintf('low_robM length: %d\n', length(low_robM));
    fprintf('up_optCau length: %d\n', length(up_optCau));
    fprintf('low_optCau length: %d\n', length(low_optCau));
    fprintf('Time values: [%s]\n', num2str(t, '%.1f '));
    fprintf('up_robM values: [%s]\n', num2str(up_robM, '%.2f '));
    fprintf('low_robM values: [%s]\n', num2str(low_robM, '%.2f '));

    % --- 表示するシグナル数の決定 ---
    % 要件: シグナルが1~3個ならその数を表示。4個以上なら先頭3個のみ表示。
    max_display_signals = 3;
    num_display_signals = min(num_signals, max_display_signals);
    fprintf('Number of signals requested: %d, will display: %d\n', num_signals, num_display_signals);

    fprintf('==================\n\n');

    % --- 永続的なFigureハンドルを検索 ---
    % 'CauMon_Realtime_Monitor_Figure' という名前のタグで既存のFigureを探す
    figTag = 'CauMon_Realtime_Monitor_Figure';
    % findall を使って 'figure' タイプのみを探す（他の UI 要素を誤検出しないため）
    figs = findall(0, 'Type', 'figure', 'Tag', figTag);
    if isempty(figs)
        figHandle = [];
    else
        figHandle = figs(1);
    end

    % --- Figure が存在していても、以前作成したプロット数と今回の表示数が異なる場合は再作成する ---
    % need_rebuild: true なら同一ウィンドウ内でレイアウトを再構築する
    need_rebuild = false;
    if ~isempty(figHandle)
        % findobj は配列を返す場合があるので、先頭の Figure を扱う
        figHandle = figHandle(1);
        try
            existingHandles = getappdata(figHandle, 'PlotHandles');
        catch
            existingHandles = [];
        end
        % 追加チェック: 実際に figure に存在する axes の数を確認
        try
            existing_axes = findall(figHandle, 'Type', 'axes');
            axes_count = length(existing_axes);
        catch
            axes_count = -1;
        end
        fprintf('visualize: found figure with %d axes (expected %d)\n', axes_count, num_display_signals + 2);
        % 以前の作成状態が存在せず、または表示すべきシグナル数が異なる場合は
        % ウィンドウを閉じるのではなく同一 Figure をクリアして再構築する
        if isempty(existingHandles)
            fprintf('visualize: found figure but no stored PlotHandles - will rebuild.\n');
            need_rebuild = true;
        elseif ~isfield(existingHandles, 'num_plotted_signals')
            fprintf('visualize: existing PlotHandles missing num_plotted_signals - will rebuild.\n');
            need_rebuild = true;
        elseif existingHandles.num_plotted_signals ~= num_display_signals
            fprintf('visualize: existing figure was created for %d signals, now need %d -> will rebuild.\n', existingHandles.num_plotted_signals, num_display_signals);
            need_rebuild = true;
        elseif axes_count ~= -1 && axes_count ~= (num_display_signals + 2)
            fprintf('visualize: axes count mismatch (%d vs expected %d) -> will rebuild.\n', axes_count, num_display_signals + 2);
            need_rebuild = true;
        end
    end

    if isempty(figHandle) || need_rebuild
         % --- 初回呼び出しまたは再作成 ---
         % グラフを新規作成し、プロットのハンドルを保存する。

         total_plots = num_display_signals + 2; % 表示するシグナル数 + ロバストネス + 因果関係

         % ★ Figureに 'Tag' を追加して作成（やや小さめのサイズで初期化を軽量化）
         % 高さは 220 + 120*total_plots 程度に抑える
         if isempty(figHandle)
             fprintf('visualize: creating new figure for %d display signals (total plots=%d)\n', num_display_signals, total_plots);
             figHandle = figure('Tag', figTag, 'Position',[100 100 900 220 + 120*total_plots]);
         else
             % 既存の Figure を保持したまま中身をクリアしてサイズを更新
             try
                fprintf('visualize: rebuilding existing figure (resetting contents) for %d display signals (total plots=%d)\n', num_display_signals, total_plots);
                % 完全にリセットして以前の tiledlayout/axes を消す
                clf(figHandle, 'reset');
                % Tag がクリアされる場合があるので再設定
                try set(figHandle, 'Tag', figTag); catch, end
                try set(figHandle, 'Position',[100 100 900 220 + 120*total_plots]); catch, end
                % 明示的に残存する tiledlayout オブジェクトを削除（安全策）
                tlds = findall(figHandle, 'Type', 'tiledlayout');
                if ~isempty(tlds)
                    delete(tlds);
                end
                % 明示的に残存する axes オブジェクトを削除（安全策）
                axs = findall(figHandle, 'Type', 'axes');
                if ~isempty(axs)
                    delete(axs);
                end
                % さらに Figure の子をすべて削除して完全にクリーンにする
                try
                    ch = get(figHandle, 'Children');
                    if ~isempty(ch)
                        delete(ch);
                    end
                catch
                end
                % 古い保存データを削除
                try
                    if isappdata(figHandle, 'PlotHandles')
                        rmappdata(figHandle, 'PlotHandles');
                    end
                catch
                end
                drawnow; % 状態を反映
             catch ME
                warning('visualize: failed to reset existing figure: %s. Creating a new one.', ME.message);
                figHandle = figure('Tag', figTag, 'Position',[100 100 900 220 + 120*total_plots]);
             end
         end

         tiledlayout(total_plots,1, 'Padding','compact', 'TileSpacing','compact');

         % ハンドルを保存するための構造体
         handles = struct();
         handles.num_plotted_signals = num_display_signals; % 実際にプロットしたシグナル数を保存
         % ウォームアップフラグ: 初期作成時は true とし、最初の実データ更新で false にする
         handles.is_warmup = true;
        % 保存用トレースはゼロ列にしておく（ウォームアップ表示はゼロ線なので比較を簡潔にする）
        try
            handles.warmup_trace = zeros(size(trace));
        catch
            handles.warmup_trace = [];
        end
        % 更新カウンタ（安全策）
        handles.update_count = 0;

        % ====== (1..N) 各シグナルをプロット ======
        handles.h_sig = gobjects(num_display_signals, 1); % シグナルプロット用ハンドル配列
        handles.ax_sig = gobjects(num_display_signals, 1); % シグナル軸用ハンドル配列
        for s = 1:num_display_signals
             handles.ax_sig(s) = nexttile; % ★ 軸ハンドルを保存

            % 描画データ決定: ウォームアップ時はゼロ線で表示（YLim は -1..1 に固定）
            try
                if isfield(handles, 'is_warmup') && handles.is_warmup
                    plot_y = zeros(size(t));
                else
                    plot_y = trace(s+1,:);
                end
            catch
                plot_y = zeros(size(t));
            end
            % ★ プロットハンドルを h_sig に保存 (インデックスは s)
            handles.h_sig(s) = plot(t, plot_y, 'LineWidth', 2); % s+1 行目がシグナルデータ

            % ★ シグナル名を使用して縦軸ラベルを設定（タイトルは表示しない）
            if s <= length(signal_name_list)
                sig_name = signal_name_list{s};
                % title を削除（不要）
                ylabel(sig_name, 'Interpreter', 'none');
            else
                % title を削除（不要）
                ylabel('Value');
            end
            xlabel('time');
            grid on;
            % シグナル描画の縦軸をウォームアップ時に固定して統一する
            set(gca, 'LineWidth', 1.5, 'FontSize', 14, 'Box', 'on', 'XColor', [1 1 1], 'YColor', [1 1 1]);
            try
                % 初期作成（ウォームアップ）では見やすさのため -1..1 にするが
                % このフラグを保持しておき、実データ受信時に自動スケールへ戻す
                set(handles.ax_sig(s), 'YLim', [-1 1], 'YLimMode', 'manual');
            catch
            end
         end

        % ====== (N+1) ロバストネスをプロット ======
        handles.ax_rob = nexttile; % ★ 軸ハンドルを保存
        hold on;

        % ★ プロットハンドルを保存
        % データ長が一致するようにインデックスを調整
        n_rob = min(length(t), length(up_robM));
        handles.h_rob_up = stairs(t(1:n_rob), up_robM(1:n_rob), 'LineWidth', 2);
        handles.h_rob_low = stairs(t(1:n_rob), low_robM(1:n_rob), 'LineWidth', 2);

        % (↓ 元のコードと同じスタイリング)
        legend({'Upper robustness','Lower robustness'}, 'Location','best');
        xlabel('time');
        ylabel('Robustness');
        % title を削除（不要）
        grid on;
        set(gca, 'LineWidth', 1.5, 'FontSize', 14, 'Box', 'on', 'XColor', [1 1 1], 'YColor', [1 1 1]);
        ylim([-100 100]);
        hold off;

        % ====== (N+2) 因果関係をプロット ======
        handles.ax_cau = nexttile; % ★ 軸ハンドルを保存
        hold on;

        % ★ プロットハンドルを保存
        % データ長が一致するようにインデックスを調整
        n_cau = min(length(t), length(up_optCau));
        handles.h_cau_up = stairs(t(1:n_cau), up_optCau(1:n_cau), 'LineWidth', 2);
        handles.h_cau_low = stairs(t(1:n_cau), low_optCau(1:n_cau), 'LineWidth', 2);

        % (↓ 元のコードと同じスタイリング)
        legend({'Violation causation','Satisfaction causation'}, 'Location','best');
        xlabel('time');
        ylabel('Causation');
        % title を削除（不要）
        grid on;
        set(gca, 'LineWidth', 1.5, 'FontSize', 14, 'Box', 'on', 'XColor', [1 1 1], 'YColor', [1 1 1]);
        hold off;

        % ====== 全体のタイトル ======
        sgtitle(strrep(phi_str, '_', '\_'), 'Interpreter', 'none', 'FontSize', 16, 'FontWeight', 'bold');

        % ----- 追加の安全チェック: 期待する軸以外を削除 -----
        try
            desired_axes = [];
            if isfield(handles, 'ax_sig') && ~isempty(handles.ax_sig)
                desired_axes = [desired_axes; handles.ax_sig(:)];
            end
            if isfield(handles, 'ax_rob') && ~isempty(handles.ax_rob)
                desired_axes = [desired_axes; handles.ax_rob];
            end
            if isfield(handles, 'ax_cau') && ~isempty(handles.ax_cau)
                desired_axes = [desired_axes; handles.ax_cau];
            end
            % 全ての axes を列挙し、desired_axes に含まれないものは削除
            all_axes = findall(figHandle, 'Type', 'axes');
            for ai = 1:length(all_axes)
                ax = all_axes(ai);
                if ~any(ax == desired_axes)
                    try delete(ax); catch, end
                end
            end
            drawnow;
        catch
        end

        % ★ 作成したすべてのハンドルをFigureに保存する
        setappdata(figHandle, 'PlotHandles', handles);

    else
        % --- 2回目以降の呼び出し (Figureが存在する場合) ---
        % グラフを再描画せず、データ (XData, YData) のみを更新する

        % 既存のFigureを一番手前に持ってくる (ウィンドウが後ろにいかないように)
        figure(figHandle);

        % ★ Figureに保存したハンドル構造体を取得
        handles = getappdata(figHandle, 'PlotHandles');
        % 更新回数をカウント（安全策: 2回目で強制解除）
        try
            if ~isfield(handles, 'update_count')
                handles.update_count = 1;
            else
                handles.update_count = handles.update_count + 1;
            end
            setappdata(figHandle, 'PlotHandles', handles);
        catch
        end
        % --- Warmup からの最初の実データ更新時に YLim を自動スケールに戻す ---
        try
            if isfield(handles, 'is_warmup') && handles.is_warmup
                % 比較方法: ウォームアップ時に保存した trace と incoming trace を比較して
                % 任意のシグナル行で最大差分が閾値を超えれば "実データ到来" とみなす
                tol = 1e-6; % 少し大きめの閾値で浮動小数点ノイズを吸収
                release = false;
                if isfield(handles, 'warmup_trace') && ~isempty(handles.warmup_trace)
                    % 比較は簡潔に: 保存はゼロ列なので、新しいデータの絶対最大値が tol を超えれば到来とみなす
                    try
                        sigRows = 2:min(1+handles.num_plotted_signals, size(trace,1));
                        if ~isempty(sigRows)% より厳密にゼロ判定するために閾値を小さくする
                            newMax = max(abs(trace(sigRows, :)), [], 'all');
                            if ~isempty(newMax) && newMax > 1e-12
                                release = true;
                            end
                        end
                    catch
                    end
                else
                    % 保存トレースがない場合は到来を即時許可
                    release = true;
                end
                % 安全策: update_count が 1 回目以降なら強制解除（最初の実更新で解除）
                try
                    if ~release && isfield(handles, 'update_count') && handles.update_count >= 1
                        release = true;
                        fprintf('visualize: forcing release due to update_count >=1.\n');
                    end
                catch
                end
                if release
                    fprintf('visualize: releasing warmup-fixed YLim on first real update (data varied).\n');
                    for s = 1:handles.num_plotted_signals
                        try
                            if isgraphics(handles.ax_sig(s))
                                set(handles.ax_sig(s), 'YLimMode', 'auto');
                            end
                        catch
                        end
                    end
                    handles.is_warmup = false;
                    % 一度解除したら warmup_trace は不要
                    if isfield(handles, 'warmup_trace')
                        handles = rmfield(handles, 'warmup_trace');
                    end
                    setappdata(figHandle, 'PlotHandles', handles);
                else
                    fprintf('visualize: still warmup-like data; keep YLim fixed.\n');
                end
            end
        catch
        end

        % もし既存ハンドルの作成時の表示数と今回の表示数が違う場合は
        % 安全のため再構築する（ここに来る前に判定済だが保険として再チェック）
        if ~isfield(handles, 'num_plotted_signals') || handles.num_plotted_signals ~= num_display_signals
            fprintf('visualize: mismatch detected at update-time (stored=%d, need=%d) -> rebuilding layout.\n', ...
                getfield(handles, 'num_plotted_signals', 0), num_display_signals);
            try
                clf(figHandle);
            catch
            end
            % force recreate by calling this function recursively once with same args
            % to build new layout in the same figure
            visualize(trace, phi_str, up_robM, low_robM, up_optCau, low_optCau, outfile, signal_names);
            return;
        end

        % ====== (1..N) Signals データを更新 ======
        % 実際に作成したシグナル数 (handles.num_plotted_signals) のみ更新する
        for s = 1:handles.num_plotted_signals
            % ハンドルが存在するか確認 (念のため)
            if isgraphics(handles.h_sig(s))
                set(handles.h_sig(s), 'XData', t, 'YData', trace(s+1,:));
                % 軸の自動スケール (Y軸のみ更新、X軸は伸びるように)
                try
                    % X 軸は常に自動で伸長させる
                    set(handles.ax_sig(s), 'XLimMode', 'auto', 'Box', 'on', 'XColor', [1 1 1], 'YColor', [1 1 1]);
                    % Y 軸はウォームアップ中は保持し、ウォームアップ解除後に自動にする
                    if ~isfield(handles, 'is_warmup') || ~handles.is_warmup
                        set(handles.ax_sig(s), 'YLimMode', 'auto');
                    end
                catch
                end
            else
                warning('Signal plot handle %d is invalid.', s);
            end
        end

        % ウォームアップ解除後は ylim を明示的に auto にして即座にスケールを反映させる
        try
            if ~isfield(handles, 'is_warmup') || ~handles.is_warmup
                for s = 1:handles.num_plotted_signals
                    try
                        if isgraphics(handles.ax_sig(s))
                            ylim(handles.ax_sig(s), 'auto');
                        end
                    catch
                    end
                end
                drawnow;
            end
        catch
        end

        % もし元のデータに表示していないシグナルが存在すれば警告
        if num_signals > handles.num_plotted_signals
            warning('Only the first %d signals are displayed; %d additional signals are ignored.', handles.num_plotted_signals, num_signals - handles.num_plotted_signals);
        end

        % 余分に存在する軸があれば非表示にする（ウォームアップ時に過剰に作られていた場合など）
        if handles.num_plotted_signals > num_display_signals
            for k = num_display_signals+1:handles.num_plotted_signals
                if isgraphics(handles.ax_sig(k))
                    set(handles.ax_sig(k), 'Visible', 'off');
                end
                if isgraphics(handles.h_sig(k))
                    set(handles.h_sig(k), 'Visible', 'off');
                end
            end
        end

        % ====== (N+1) Robustness データを更新 ======
         if isgraphics(handles.h_rob_up) && isgraphics(handles.h_rob_low)
            n_rob = min(length(t), length(up_robM));
            set(handles.h_rob_up, 'XData', t(1:n_rob), 'YData', up_robM(1:n_rob));
            set(handles.h_rob_low, 'XData', t(1:n_rob), 'YData', low_robM(1:n_rob));
            set(handles.ax_rob, 'XLimMode', 'auto', 'YLim', [-100 100], 'Box', 'on', 'XColor', [1 1 1], 'YColor', [1 1 1]);
         else
             warning('Robustness plot handle is invalid.');
         end

        % ====== (N+2) Causation データを更新 ======
        if isgraphics(handles.h_cau_up) && isgraphics(handles.h_cau_low)
            n_cau = min(length(t), length(up_optCau));
            set(handles.h_cau_up, 'XData', t(1:n_cau), 'YData', up_optCau(1:n_cau));
            set(handles.h_cau_low, 'XData', t(1:n_cau), 'YData', low_optCau(1:n_cau));
            set(handles.ax_cau, 'XLimMode', 'auto', 'YLimMode', 'auto', 'Box', 'on', 'XColor', [1 1 1], 'YColor', [1 1 1]);
        else
             warning('Causation plot handle is invalid.');
        end

        % ★ 変更を画面に反映
        drawnow limitrate; % limitrate オプションで更新頻度を調整
    end

    % この関数内では基本的に描画更新のみを行う。
    % outfile が非空の場合のみ、図をファイルに保存する（終了時など）。
    if ~isempty(outfile) && isgraphics(figHandle)
        try
            % リアルタイム監視用途として軽めの解像度で保存
            exportgraphics(figHandle, outfile, 'Resolution', 150);
        catch ME
            warning('Failed to save figure to %s: %s', outfile, ME.message);
        end
    end
end
