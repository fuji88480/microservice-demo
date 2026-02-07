import { ApiError } from '@/api/client';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { useRegister } from '@/hooks/useRegister';
import { Label } from '@radix-ui/react-label';
import { useState, type FormEvent } from 'react';

type Message = {
  type: 'success' | 'error';
  text: string;
};
export default function Signup() {
  const { mutateAsync, isPending } = useRegister();
  const [message, setMessage] = useState<Message | null>(null);

  const handleSubmit = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    const fd = new FormData(e.currentTarget);
    try {
      await mutateAsync({
        email: fd.get('email') as string,
        password: fd.get('password') as string,
      });
      setMessage({ type: 'success', text: '登録できました' });
    } catch (err) {
      const message =
        err instanceof ApiError ? err.message : '登録に失敗しました';
      setMessage({ type: 'error', text: message });
    }
  };

  return (
    <div className="flex justify-center font-mono">
      {isPending && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40">
          <div className="rounded-xl bg-white px-6 py-4 text-sm shadow">
            登録中です...
          </div>
        </div>
      )}

      <div className="space-y-6 p-10 mt-50 rounded-2xl border">
        <div className="space-y-2">
          <h1 className="text-2xl">新規登録</h1>
          <p className="text-sm text-muted-foreground">
            新しいアカウントを作成
          </p>
        </div>

        {message && (
          <div
            className={`rounded-md px-3 py-2 text-sm ${
              message.type === 'success'
                ? 'bg-green-100 text-green-700'
                : 'bg-red-100 text-red-700'
            }`}
          >
            {message.text}
          </div>
        )}

        <form className="space-y-4" onSubmit={handleSubmit}>
          <div>
            <Label htmlFor="email">E-Mail</Label>
            <Input
              id="email"
              type="email"
              name="email"
              placeholder="you@example.com"
            />
          </div>

          <div className="mb-10">
            <Label htmlFor="password">Password</Label>
            <Input
              id="password"
              type="password"
              name="password"
              placeholder="********"
            />
          </div>

          <Button
            disabled={isPending}
            variant="destructive"
            className="w-full bg-pink-700"
          >
            新規登録
          </Button>
        </form>
      </div>
    </div>
  );
}
